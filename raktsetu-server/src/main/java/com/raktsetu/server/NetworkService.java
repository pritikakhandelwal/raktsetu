package com.raktsetu.server;

import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.model.AuditEvent;
import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.CompatibilityResult;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.NetworkData;
import com.raktsetu.domain.model.WastageRecord;
import com.raktsetu.engine.BloodNetwork;
import com.raktsetu.engine.Freshness;
import com.raktsetu.engine.RequestService;
import com.raktsetu.engine.RiskItem;
import com.raktsetu.engine.TransferService;
import com.raktsetu.engine.strategy.RedistributionEngine;
import com.raktsetu.engine.strategy.StrategyComparison;
import com.raktsetu.persistence.AuditRecord;
import com.raktsetu.server.ApiModels.AddUnitsInput;
import com.raktsetu.server.db.AuditRepository;
import com.raktsetu.server.db.NetworkRepository;
import com.raktsetu.server.db.StateRepository;
import com.raktsetu.server.db.TransferRepository;
import com.raktsetu.server.ApiModels.AddedUnits;
import com.raktsetu.server.ApiModels.AuditEntry;
import com.raktsetu.server.ApiModels.AuditView;
import com.raktsetu.server.ApiModels.CompatibilityInput;
import com.raktsetu.server.ApiModels.CompatibilityView;
import com.raktsetu.server.ApiModels.ComparisonView;
import com.raktsetu.server.ApiModels.FacilityStock;
import com.raktsetu.server.ApiModels.FacilityView;
import com.raktsetu.server.ApiModels.NetworkStock;
import com.raktsetu.server.ApiModels.NetworkStockRow;
import com.raktsetu.server.ApiModels.RiskView;
import com.raktsetu.server.ApiModels.SimulationRow;
import com.raktsetu.server.ApiModels.SimulationView;
import com.raktsetu.server.ApiModels.StockLine;
import com.raktsetu.server.ApiModels.StrategyView;
import com.raktsetu.server.ApiModels.Summary;
import com.raktsetu.server.ApiModels.WastageView;
import com.raktsetu.sim.SampleNetworkFactory;
import com.raktsetu.sim.SimulationResult;
import com.raktsetu.sim.Simulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The web application's view of one blood network.
 *
 * <p>The engine works on an in-memory network; the database is the source of truth. Every command runs through
 * {@link #change}: the command's audit events are appended to the hash-chained audit log and applied to the tables in
 * one transaction. If writing fails, the in-memory network is reloaded from the database, so the two never drift
 * apart. Methods are synchronized because one network is shared by all users of this server.
 */
@Service
public class NetworkService {

    private static final int URGENT_DAYS = 2;
    private static final int MAX_SIMULATION_DAYS = 365;

    /** A command that changes the network. */
    @FunctionalInterface
    interface Command<T> {
        T run() throws RaktSetuException;
    }

    /** Extra database writes that belong to the same transaction as a command, given the command's result. */
    @FunctionalInterface
    interface DbWrite<T> {
        void write(T result);
    }

    private final NetworkRepository networkRepo;
    private final AuditRepository auditRepo;
    private final StateRepository state;
    private final TransferRepository transferRepo;
    private final TransactionTemplate tx;
    private final CapturingAuditTrail capture = new CapturingAuditTrail();
    private final RedistributionEngine engine = RedistributionEngine.withDefaultStrategies();
    private final long seed;
    private BloodNetwork network;
    private RequestService requests;
    private TransferService transfers;
    private LocalDate today;

    public NetworkService(RaktSetuProperties properties, NetworkRepository networkRepo, AuditRepository auditRepo,
                          StateRepository state, TransferRepository transferRepo, TransactionTemplate tx)
            throws RaktSetuException {
        this.networkRepo = networkRepo;
        this.auditRepo = auditRepo;
        this.state = state;
        this.transferRepo = transferRepo;
        this.tx = tx;
        this.seed = properties.seed();
        this.today = startDate(properties);
        if (networkRepo.isEmpty()) {
            NetworkData sample = SampleNetworkFactory.create(today, seed);
            tx.executeWithoutResult(s -> {
                networkRepo.seed(sample);
                state.put(StateRepository.NEXT_UNIT_SERIAL, String.valueOf(sample.nextUnitSerial()));
            });
        }
        reload();
        change(() -> network.removeExpired(today));
    }

    /** The configured date, else a demo date saved earlier (if not in the past), else the system date. */
    private LocalDate startDate(RaktSetuProperties properties) {
        if (properties.today() != null) {
            return properties.today();
        }
        LocalDate system = properties.startDate();
        return state.get(StateRepository.TODAY).map(LocalDate::parse).filter(d -> !d.isBefore(system))
                .orElse(system);
    }

    // ------------------------------------------------------------------ overview

    public synchronized Summary summary() throws RaktSetuException {
        int units = 0;
        int urgent = 0;
        for (Facility f : network.storingFacilities()) {
            units += f.getInventory().totalUnits();
            urgent += urgentUnits(f);
        }
        int atRisk = engine.risks(network, today).stream().mapToInt(r -> r.atRisk().size()).sum();
        int hospitals = network.facilities().size() - network.storingFacilities().size();
        return new Summary(today, network.storingFacilities().size(), hospitals, units, urgent, atRisk,
                network.wastage().size(), transferRepo.countProposed(), auditRepo.verify().intact());
    }

    public synchronized List<FacilityView> facilities() {
        Map<String, Integer> atRisk = atRiskByFacility();
        return network.facilities().stream().map(f -> view(f, atRisk)).toList();
    }

    public synchronized FacilityStock facilityStock(String id) throws RaktSetuException {
        Facility f = network.facility(id);
        List<StockLine> lines = new ArrayList<>();
        if (f.canStore()) {
            for (ComponentType type : ComponentType.values()) {
                for (BloodGroup group : BloodGroup.values()) {
                    List<BloodComponent> units = f.getInventory().units(type, group);
                    if (!units.isEmpty()) {
                        BloodComponent first = units.get(0);
                        long left = first.daysToExpiry(today);
                        lines.add(new StockLine(type.name(), group.label(), units.size(), first.getExpiryDate(), left,
                                left <= URGENT_DAYS));
                    }
                }
            }
        }
        return new FacilityStock(view(f, atRiskByFacility()), lines);
    }

    public synchronized NetworkStock networkStock() {
        List<String> groups = new ArrayList<>();
        for (BloodGroup g : BloodGroup.values()) {
            groups.add(g.label());
        }
        List<NetworkStockRow> rows = new ArrayList<>();
        List<String> low = new ArrayList<>();
        for (ComponentType type : ComponentType.values()) {
            List<Integer> counts = new ArrayList<>();
            int total = 0;
            for (BloodGroup g : BloodGroup.values()) {
                int n = network.totalUsable(type, g, today);
                counts.add(n);
                total += n;
                double daily = 0;
                for (Facility f : network.storingFacilities()) {
                    daily += network.usage().averageDaily(f.getId(), type, g, today);
                }
                if (daily > 0 && n < daily * 2) {
                    low.add(String.format(Locale.ROOT, "%s %s: %d units, about %.1f days of use", g, type, n,
                            n / daily));
                }
            }
            rows.add(new NetworkStockRow(type.name(), counts, total));
        }
        return new NetworkStock(groups, rows, low);
    }

    public synchronized WastageView wastage() {
        Map<String, Integer> byComponent = new TreeMap<>();
        Map<String, Integer> byFacility = new TreeMap<>();
        for (WastageRecord w : network.wastage()) {
            byComponent.merge(w.component().name(), 1, Integer::sum);
            byFacility.merge(w.facilityId(), 1, Integer::sum);
        }
        return new WastageView(network.wastage().size(), byComponent, byFacility);
    }

    // ------------------------------------------------------------------ changes to stock

    public synchronized AddedUnits addUnits(String facilityId, AddUnitsInput input) throws RaktSetuException {
        require(input, "request body");
        ComponentType type = ComponentType.parse(input.component());
        BloodGroup group = BloodGroup.parse(input.group());
        LocalDate collected = input.collectedOn() == null ? today : input.collectedOn();
        int quantity = requirePositive(input.quantity(), "quantity", 500);
        List<BloodComponent> added = change(
                () -> network.addUnits(facilityId, type, group, collected, quantity, today));
        return new AddedUnits(added.stream().map(BloodComponent::getId).toList(), added.get(0).getExpiryDate());
    }

    public synchronized FacilityView recordReport(String facilityId) throws RaktSetuException {
        Facility f = change(() -> {
            Facility facility = network.facility(facilityId);
            facility.markReported(today);
            return facility;
        });
        return view(f, atRiskByFacility());
    }

    public synchronized CompatibilityView compatibility(CompatibilityInput input) {
        require(input, "request body");
        ComponentType type = ComponentType.parse(input.component());
        BloodGroup donor = BloodGroup.parse(input.donor());
        BloodGroup recipient = BloodGroup.parse(input.recipient());
        CompatibilityResult r = type.newUnit("CHECK", donor, today).compatibilityWith(recipient);
        return new CompatibilityView(r.isAllowed(), r.level().name(), r.reason());
    }

    // ------------------------------------------------------------------ risk and redistribution

    public synchronized List<RiskView> risks() {
        return engine.risks(network, today).stream()
                .map(r -> new RiskView(r.facility().getId(), r.facility().getName(), r.component().name(),
                        r.group().label(), r.atRisk().size(), r.usableStock(), r.soonestDaysLeft(),
                        r.averageDaily(), r.reason()))
                .toList();
    }

    public synchronized List<StrategyView> strategies() {
        return engine.strategies().stream().map(s -> new StrategyView(s.name(), s.description())).toList();
    }

    public synchronized List<ComparisonView> compare() {
        List<ComparisonView> result = new ArrayList<>();
        for (StrategyComparison c : engine.compare(network, today)) {
            result.add(new ComparisonView(c.strategy(), c.orders().size(), c.unitsRescued(), c.stillAtRisk(),
                    c.totalKm()));
        }
        return result;
    }

    // ------------------------------------------------------------------ audit, simulation, date

    public synchronized AuditView audit(int limit) {
        List<AuditEntry> entries = new ArrayList<>();
        for (AuditRecord r : auditRepo.latest(limit)) {
            var e = r.event();
            entries.add(new AuditEntry(r.sequence(), e.date(), e.type().name(), e.facilityId(), e.counterparty(),
                    e.component().name(), e.group().label(), e.unitIds().size(), e.note(), r.hash()));
        }
        AuditRepository.Verification v = auditRepo.verify();
        return new AuditView(auditRepo.count(), v.intact(), v.message(), entries);
    }

    public synchronized SimulationView simulate(Integer days) throws RaktSetuException {
        int n = days == null ? Simulator.DEFAULT_DAYS : requirePositive(days, "days", MAX_SIMULATION_DAYS);
        List<SimulationResult> results = new Simulator(today, n, seed).compareAll();
        SimulationResult base = results.get(0);
        List<SimulationRow> rows = new ArrayList<>();
        for (SimulationResult r : results) {
            Map<String, Integer> byComponent = new LinkedHashMap<>();
            for (ComponentType t : ComponentType.values()) {
                byComponent.put(t.name(), r.expired(t));
            }
            rows.add(new SimulationRow(r.policy(), r.unitsExpired(), byComponent, r.requestsFulfilled(),
                    r.requestsUnmet(), r.unitsTransferred(), r.wastageReductionVersus(base)));
        }
        return new SimulationView(n, seed, today, rows);
    }

    /** Moves "today" forward and removes anything that expired. */
    public synchronized Summary advanceTo(LocalDate date) throws RaktSetuException {
        require(date, "today");
        if (date.isBefore(today)) {
            throw new IllegalArgumentException("The date can only move forward; stock has already been processed up to "
                    + today);
        }
        today = date;
        change(() -> network.removeExpired(today));
        return summary();
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Runs a command on the in-memory network and writes its effects to the database in one transaction. Effects are
     * written even when the command then fails (for example, expired units discarded before a request that could not
     * be met). If writing fails, the network is reloaded from the database and the error is rethrown.
     */
    <T> T change(Command<T> command) throws RaktSetuException {
        return change(command, result -> { });
    }

    /** As {@link #change(Command)}, also running {@code after} in the same transaction if the command succeeded. */
    <T> T change(Command<T> command, DbWrite<T> after) throws RaktSetuException {
        capture.clear();
        T result = null;
        RaktSetuException refused = null;
        try {
            result = command.run();
        } catch (RaktSetuException e) {
            refused = e;
        }
        List<AuditEvent> events = capture.drain();
        T resultFinal = result;
        RaktSetuException refusedFinal = refused;
        try {
            tx.executeWithoutResult(s -> {
                auditRepo.append(events);
                networkRepo.apply(events, network);
                networkRepo.saveReportDates(network.facilities());
                state.put(StateRepository.NEXT_UNIT_SERIAL, String.valueOf(network.snapshot().nextUnitSerial()));
                state.put(StateRepository.TODAY, today.toString());
                if (refusedFinal == null) {
                    after.write(resultFinal);
                }
            });
        } catch (RuntimeException e) {
            reload();
            throw e;
        }
        if (refused != null) {
            throw refused;
        }
        return result;
    }

    /** Rebuilds the in-memory network from the database. */
    private void reload() {
        long serial = state.get(StateRepository.NEXT_UNIT_SERIAL).map(Long::parseLong).orElse(1L);
        network = new BloodNetwork(networkRepo.load(serial), capture);
        requests = new RequestService(network);
        transfers = new TransferService(network);
    }

    // ------------------------------------------------------------------ for WorkflowService (same lock: this object)

    BloodNetwork network() {
        return network;
    }

    LocalDate today() {
        return today;
    }

    RequestService requests() {
        return requests;
    }

    TransferService transfers() {
        return transfers;
    }

    RedistributionEngine engine() {
        return engine;
    }

    int urgentUnitsAt(Facility f) {
        return urgentUnits(f);
    }

    private Map<String, Integer> atRiskByFacility() {
        Map<String, Integer> map = new HashMap<>();
        for (RiskItem r : engine.risks(network, today)) {
            map.merge(r.facility().getId(), r.atRisk().size(), Integer::sum);
        }
        return map;
    }

    private int urgentUnits(Facility f) {
        if (!f.canStore()) {
            return 0;
        }
        return (int) f.getInventory().allUnits().stream().filter(u -> u.daysToExpiry(today) <= URGENT_DAYS).count();
    }

    private FacilityView view(Facility f, Map<String, Integer> atRisk) {
        long days = f.daysSinceReport(today);
        return new FacilityView(f.getId(), f.getName(), f.getFacilityType().name(), f.getLastReportedOn(),
                days == Long.MAX_VALUE ? null : days, Freshness.of(f, today).name(),
                f.canStore() ? f.getInventory().totalUnits() : 0, urgentUnits(f), atRisk.getOrDefault(f.getId(), 0));
    }

    private static <T> T require(T value, String name) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static int requirePositive(Integer value, String name, int max) {
        require(value, name);
        if (value < 1 || value > max) {
            throw new IllegalArgumentException(name + " must be between 1 and " + max);
        }
        return value;
    }
}
