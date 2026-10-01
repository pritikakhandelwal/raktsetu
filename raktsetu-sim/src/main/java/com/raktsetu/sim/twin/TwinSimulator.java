package com.raktsetu.sim.twin;

import com.raktsetu.domain.exception.InsufficientStockException;
import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.interfaces.AuditTrail;
import com.raktsetu.domain.model.AuditEvent;
import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.BloodRequest;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.TransferOrder;
import com.raktsetu.domain.model.WastageRecord;
import com.raktsetu.engine.Allocation;
import com.raktsetu.engine.BloodNetwork;
import com.raktsetu.engine.InMemoryAuditTrail;
import com.raktsetu.engine.RequestOutcome;
import com.raktsetu.engine.RequestService;
import com.raktsetu.engine.TransferService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The digital twin: simulates one network day by day, keeping the <em>true</em> stock separate from what the planner
 * <em>observes</em> through centres' reports.
 *
 * <p>Each day, in order: expired units are discarded; centres receive their routine supply; hospitals' patient
 * requests are served from true stock (a centre always knows its own shelves); some centres report their stock
 * (depending on compliance); then the policy plans transfers from the observed network, and the simulator carries out
 * whatever is still physically possible. Measurement starts after the warm-up.
 */
public final class TwinSimulator {

    /** One run's result, plus the final state and (optionally) every event, for checks. */
    public record Run(TwinResult result, BloodNetwork truth, List<AuditEvent> events) {
    }

    private final NetworkSpec spec;
    private final TwinSettings settings;
    private final Schedule schedule;
    private final Map<String, Site> sites;

    public TwinSimulator(NetworkSpec spec, TwinSettings settings) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.schedule = Schedule.generate(spec, settings);
        this.sites = spec.sites().stream().collect(Collectors.toMap(Site::id, Function.identity()));
    }

    public TwinResult run(TwinPolicy policy) throws RaktSetuException {
        return run(policy, false).result();
    }

    /**
     * Runs the simulation.
     *
     * @param recordEvents keep every audit event (memory-hungry; for checks and tests)
     */
    public Run run(TwinPolicy policy, boolean recordEvents) throws RaktSetuException {
        InMemoryAuditTrail recorder = recordEvents ? new InMemoryAuditTrail() : null;
        AuditTrail trail = recorder != null ? recorder : event -> { };
        BloodNetwork truth = new BloodNetwork(spec.emptyNetwork(settings.start()), trail);
        RequestService requests = new RequestService(truth);
        TransferService transfers = new TransferService(truth);
        ObservedState observed = new ObservedState();
        Counters c = new Counters();

        for (int d = 0; d < settings.totalDays(); d++) {
            LocalDate today = settings.start().plusDays(d);
            boolean measured = d >= settings.warmupDays();
            Schedule.Day day = schedule.day(d);

            for (WastageRecord w : truth.removeExpired(today)) {
                if (measured) {
                    c.expired[w.component().ordinal()]++;
                }
            }
            for (Schedule.Supply s : day.supplies()) {
                truth.addUnits(s.centreId(), s.type(), s.group(), today, s.units(), today);
                if (measured) {
                    c.collected[s.type().ordinal()] += s.units();
                }
            }
            List<TwinPolicy.Shortfall> shortfalls = new ArrayList<>();
            for (BloodRequest r : day.requests()) {
                serve(r, today, requests, measured, c, shortfalls);
            }
            for (String id : day.reporting()) {
                Facility centre = truth.facility(id);
                centre.markReported(today);
                observed.report(centre, truth.usage(), today);
            }
            if (policy != TwinPolicy.NONE) {
                BloodNetwork view = observed.view(truth.facilities(), spec.routes());
                for (TransferOrder planned : policy.plan(view, shortfalls, today)) {
                    execute(planned, truth, transfers, today, measured, c);
                }
            }
        }

        TwinResult result = new TwinResult(policy.name(), settings.compliance(), settings.replication(),
                settings.days(), list(c.collected), c.issued, list(c.expired), c.requests, c.requestsUnmet, c.unitsRequested,
                c.unitsUnmet, c.substituted, c.oNegativeToOthers, c.planned, c.moved, c.km, c.urbanRequested,
                c.urbanUnmet, c.ruralRequested, c.ruralUnmet);
        return new Run(result, truth, recorder == null ? List.of() : recorder.events());
    }

    private void serve(BloodRequest r, LocalDate today, RequestService requests, boolean measured, Counters c,
                       List<TwinPolicy.Shortfall> shortfalls) throws RaktSetuException {
        boolean rural = sites.get(r.hospitalId()).rural();
        try {
            RequestOutcome outcome = requests.handle(r, today);
            if (measured) {
                c.issued += outcome.unitsIssued();
                for (Allocation a : outcome.allocations()) {
                    if (a.isSubstitute()) {
                        c.substituted += a.units().size();
                    }
                    if (a.donorGroup() == BloodGroup.O_NEG && r.recipient() != BloodGroup.O_NEG) {
                        c.oNegativeToOthers += a.units().size();
                    }
                }
            }
        } catch (InsufficientStockException e) {
            shortfalls.add(new TwinPolicy.Shortfall(r.hospitalId(), r.component(), r.recipient(), r.quantity()));
            if (measured) {
                c.requestsUnmet++;
                c.unitsUnmet += r.quantity();
                if (rural) {
                    c.ruralUnmet += r.quantity();
                } else {
                    c.urbanUnmet += r.quantity();
                }
            }
        }
        if (measured) {
            c.requests++;
            c.unitsRequested += r.quantity();
            if (rural) {
                c.ruralRequested += r.quantity();
            } else {
                c.urbanRequested += r.quantity();
            }
        }
    }

    /**
     * Carries out a planned transfer on the true network. Units that are no longer at the source, or that would arrive
     * with less than a day left, are dropped: this is where stale data costs something.
     */
    private static void execute(TransferOrder planned, BloodNetwork truth, TransferService transfers, LocalDate today,
                                boolean measured, Counters c) throws RaktSetuException {
        Facility source = truth.facility(planned.getSource().getId());
        Facility destination = truth.facility(planned.getDestination().getId());
        List<BloodComponent> present = planned.getUnits().stream()
                .filter(u -> source.getInventory().find(u.getId()).isPresent() && u.daysToExpiry(today) >= 1)
                .toList();
        if (measured) {
            c.planned += planned.unitCount();
        }
        if (present.isEmpty()) {
            return;
        }
        TransferOrder real = new TransferOrder(planned.getId(), source, destination, present, today,
                planned.getRoute(), planned.getReason());
        transfers.approve(real, today);
        if (measured) {
            c.moved += present.size();
            c.km += planned.getRoute().distanceKm();
        }
    }

    private static List<Integer> list(int[] counts) {
        List<Integer> list = new ArrayList<>(counts.length);
        for (int n : counts) {
            list.add(n);
        }
        return list;
    }

    /** Running totals for one run. */
    private static final class Counters {
        final int[] expired = new int[ComponentType.values().length];
        final int[] collected = new int[ComponentType.values().length];
        int issued;
        int requests;
        int requestsUnmet;
        int unitsRequested;
        int unitsUnmet;
        int substituted;
        int oNegativeToOthers;
        int planned;
        int moved;
        int km;
        int urbanRequested;
        int urbanUnmet;
        int ruralRequested;
        int ruralUnmet;
    }
}
