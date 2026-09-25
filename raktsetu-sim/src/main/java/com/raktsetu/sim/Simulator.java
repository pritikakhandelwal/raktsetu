package com.raktsetu.sim;

import com.raktsetu.domain.exception.InsufficientStockException;
import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.BloodRequest;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.FacilityType;
import com.raktsetu.domain.model.TransferOrder;
import com.raktsetu.domain.model.WastageRecord;
import com.raktsetu.engine.BloodNetwork;
import com.raktsetu.engine.InMemoryAuditTrail;
import com.raktsetu.engine.RequestOutcome;
import com.raktsetu.engine.RequestService;
import com.raktsetu.engine.TransferService;
import com.raktsetu.engine.strategy.ExpiryFirstStrategy;
import com.raktsetu.engine.strategy.NearestFirstStrategy;
import com.raktsetu.engine.strategy.RedistributionEngine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Simulates the sample network day by day, once without redistribution and once per strategy.
 *
 * <p>Every run starts from the same network and sees exactly the same supply and demand (common random numbers), so
 * differences between runs come only from the policy. Each day: expired units are removed, every storage centre
 * receives the same routine supply (the "push" distribution that causes mismatches), hospitals raise emergency
 * requests, and, if a strategy is used, its whole plan is approved.
 */
public final class Simulator {

    public static final int DEFAULT_DAYS = 30;
    public static final long DEFAULT_SEED = 42;
    public static final String NO_REDISTRIBUTION = "no redistribution";

    /** Supply exceeds demand by this factor, as it does in India overall. */
    static final double SUPPLY_FACTOR = 1.10;

    private record Supply(String facilityId, ComponentType type, BloodGroup group, int units) {
    }

    private record Day(List<Supply> supplies, List<BloodRequest> requests) {
    }

    private final LocalDate start;
    private final int days;
    private final long seed;
    private final List<Day> schedule;

    public Simulator(LocalDate start, int days, long seed) {
        this.start = Objects.requireNonNull(start, "start");
        if (days < 1) {
            throw new IllegalArgumentException("Simulate at least one day");
        }
        this.days = days;
        this.seed = seed;
        this.schedule = generateSchedule(new RandomSource(seed ^ 0x5DEECE66DL));
    }

    /** Runs without redistribution and with each strategy, in that order. */
    public List<SimulationResult> compareAll() throws RaktSetuException {
        return List.of(run(null), run(NearestFirstStrategy.NAME), run(ExpiryFirstStrategy.NAME));
    }

    /**
     * Runs the simulation.
     *
     * @param strategy strategy name, or {@code null} for no redistribution
     */
    public SimulationResult run(String strategy) throws RaktSetuException {
        BloodNetwork network = new BloodNetwork(SampleNetworkFactory.create(start, seed), new InMemoryAuditTrail());
        RequestService requests = new RequestService(network);
        RedistributionEngine engine = RedistributionEngine.withDefaultStrategies();
        TransferService transfers = new TransferService(network);

        Map<ComponentType, Integer> expired = new EnumMap<>(ComponentType.class);
        int collected = 0;
        int fulfilled = 0;
        int unmet = 0;
        int issued = 0;
        int transferred = 0;
        for (int d = 0; d < days; d++) {
            LocalDate today = start.plusDays(d);
            Day day = schedule.get(d);
            for (WastageRecord w : network.removeExpired(today)) {
                expired.merge(w.component(), 1, Integer::sum);
            }
            for (Supply s : day.supplies()) {
                network.addUnits(s.facilityId(), s.type(), s.group(), today, s.units(), today);
                collected += s.units();
            }
            for (Facility f : network.facilities()) {
                f.markReported(today);
            }
            for (BloodRequest r : day.requests()) {
                try {
                    RequestOutcome outcome = requests.handle(r, today);
                    fulfilled++;
                    issued += outcome.unitsIssued();
                } catch (InsufficientStockException e) {
                    unmet++;
                }
            }
            if (strategy != null) {
                for (TransferOrder order : engine.plan(strategy, network, today)) {
                    transfers.approve(order, today);
                    transferred += order.unitCount();
                }
            }
        }
        return new SimulationResult(strategy == null ? NO_REDISTRIBUTION : strategy, days, collected, expired,
                fulfilled, unmet, issued, transferred);
    }

    private List<Day> generateSchedule(RandomSource random) {
        List<SampleNetworkFactory.Site> centres = SampleNetworkFactory.SITES.stream()
                .filter(s -> s.type() != FacilityType.HOSPITAL).toList();
        List<SampleNetworkFactory.Site> hospitals = SampleNetworkFactory.SITES.stream()
                .filter(s -> s.type() == FacilityType.HOSPITAL).toList();
        double totalDemand = hospitals.stream().mapToDouble(SampleNetworkFactory.Site::demand).sum();

        List<Day> result = new ArrayList<>(days);
        for (int d = 0; d < days; d++) {
            List<Supply> supplies = new ArrayList<>();
            for (SampleNetworkFactory.Site c : centres) {
                for (ComponentType type : ComponentType.values()) {
                    double perCentre = SampleNetworkFactory.DAILY_DEMAND.get(type) * totalDemand * SUPPLY_FACTOR
                            / centres.size();
                    for (BloodGroup g : BloodGroup.values()) {
                        int n = random.poisson(perCentre * RandomSource.GROUP_SHARE.get(g));
                        if (n > 0) {
                            supplies.add(new Supply(c.id(), type, g, n));
                        }
                    }
                }
            }
            List<BloodRequest> requests = new ArrayList<>();
            for (SampleNetworkFactory.Site h : hospitals) {
                for (ComponentType type : ComponentType.values()) {
                    for (BloodGroup g : BloodGroup.values()) {
                        int n = random.poisson(SampleNetworkFactory.DAILY_DEMAND.get(type) * h.demand()
                                * RandomSource.GROUP_SHARE.get(g));
                        if (n > 0) {
                            requests.add(new BloodRequest(h.id(), type, g, n));
                        }
                    }
                }
            }
            result.add(new Day(supplies, requests));
        }
        return result;
    }
}
