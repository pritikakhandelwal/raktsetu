package com.raktsetu.server;

import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.BloodRequest;
import com.raktsetu.domain.model.CompatibilityResult;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.LoadResult;
import com.raktsetu.domain.model.TransferOrder;
import com.raktsetu.domain.model.TransferStatus;
import com.raktsetu.domain.model.Urgency;
import com.raktsetu.domain.model.WastageRecord;
import com.raktsetu.engine.Allocation;
import com.raktsetu.engine.BloodNetwork;
import com.raktsetu.engine.Freshness;
import com.raktsetu.engine.RequestOutcome;
import com.raktsetu.engine.RequestService;
import com.raktsetu.engine.RiskItem;
import com.raktsetu.engine.TransferService;
import com.raktsetu.engine.strategy.RedistributionEngine;
import com.raktsetu.engine.strategy.StrategyComparison;
import com.raktsetu.persistence.AuditLogger;
import com.raktsetu.persistence.AuditRecord;
import com.raktsetu.persistence.DataLoader;
import com.raktsetu.server.ApiModels.AddUnitsInput;
import com.raktsetu.server.ApiModels.AddedUnits;
import com.raktsetu.server.ApiModels.AllocationView;
import com.raktsetu.server.ApiModels.AuditEntry;
import com.raktsetu.server.ApiModels.AuditView;
import com.raktsetu.server.ApiModels.BloodRequestInput;
import com.raktsetu.server.ApiModels.CompatibilityInput;
import com.raktsetu.server.ApiModels.CompatibilityView;
import com.raktsetu.server.ApiModels.ComparisonView;
import com.raktsetu.server.ApiModels.FacilityStock;
import com.raktsetu.server.ApiModels.FacilityView;
import com.raktsetu.server.ApiModels.NetworkStock;
import com.raktsetu.server.ApiModels.NetworkStockRow;
import com.raktsetu.server.ApiModels.RequestResult;
import com.raktsetu.server.ApiModels.RiskView;
import com.raktsetu.server.ApiModels.SimulationRow;
import com.raktsetu.server.ApiModels.SimulationView;
import com.raktsetu.server.ApiModels.StockLine;
import com.raktsetu.server.ApiModels.StrategyView;
import com.raktsetu.server.ApiModels.Summary;
import com.raktsetu.server.ApiModels.TransferView;
import com.raktsetu.server.ApiModels.WastageView;
import com.raktsetu.sim.SampleNetworkFactory;
import com.raktsetu.sim.SimulationResult;
import com.raktsetu.sim.Simulator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The web application's view of one blood network. It holds the network in memory, saves it after every change, and
 * turns engine results into API models. Methods are synchronized because one network is shared by all requests; the
 * roadmap's Phase 5 replaces this with an event store and reservations.
 */
@Service
public class NetworkService {

    static final String AUDIT_FILE = "audit_log.csv";
    private static final int URGENT_DAYS = 2;
    private static final int MAX_SIMULATION_DAYS = 365;

    private final DataLoader loader;
    private final AuditLogger auditLog;
    private final BloodNetwork network;
    private final RequestService requests;
    private final TransferService transfers;
    private final RedistributionEngine engine = RedistributionEngine.withDefaultStrategies();
    private final long seed;
    private final Map<String, TransferOrder> plan = new LinkedHashMap<>();
    private LocalDate today;

    public NetworkService(RaktSetuProperties properties) throws RaktSetuException {
        this.today = properties.startDate();
        this.seed = properties.seed();
        this.loader = new DataLoader(properties.dataDir());
        if (!loader.hasData()) {
            loader.save(SampleNetworkFactory.create(today, seed));
        }
        LoadResult loaded = loader.load();
        this.auditLog = new AuditLogger(properties.dataDir().resolve(AUDIT_FILE));
        this.network = new BloodNetwork(loaded.data(), auditLog);
        this.requests = new RequestService(network);
        this.transfers = new TransferService(network);
        network.removeExpired(today);
        save();
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
                network.wastage().size(), pendingTransfers().size(), auditLog.verify().intact());
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
        List<BloodComponent> added = network.addUnits(facilityId, type, group, collected, quantity, today);
        save();
        return new AddedUnits(added.stream().map(BloodComponent::getId).toList(), added.get(0).getExpiryDate());
    }

    public synchronized FacilityView recordReport(String facilityId) throws RaktSetuException {
        Facility f = network.facility(facilityId);
        f.markReported(today);
        save();
        return view(f, atRiskByFacility());
    }

    public synchronized RequestResult request(BloodRequestInput input) throws RaktSetuException {
        require(input, "request body");
        require(input.hospitalId(), "hospitalId");
        ComponentType type = ComponentType.parse(input.component());
        BloodGroup group = BloodGroup.parse(input.group());
        int quantity = requirePositive(input.quantity(), "quantity", 50);
        Urgency urgency = input.urgency() == null || input.urgency().isBlank()
                ? Urgency.EMERGENCY : Urgency.parse(input.urgency());
        RequestOutcome outcome = requests.handle(new BloodRequest(input.hospitalId(), type, group, quantity, urgency),
                today);
        save();
        List<AllocationView> allocations = new ArrayList<>();
        for (Allocation a : outcome.allocations()) {
            allocations.add(new AllocationView(a.source().getId(), a.source().getName(), a.route().distanceKm(),
                    a.route().travelMinutes(), a.donorGroup().label(),
                    a.units().stream().map(BloodComponent::getId).toList(),
                    a.units().stream().map(u -> u.daysToExpiry(today)).toList(), a.isSubstitute(),
                    a.compatibility().reason()));
        }
        return new RequestResult(outcome.unitsIssued(), outcome.usedSubstitutes(), allocations);
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

    /** Replaces the current plan with a new one from the named strategy. */
    public synchronized List<TransferView> plan(String strategy) {
        require(strategy, "strategy");
        List<TransferOrder> orders = engine.plan(strategy, network, today);
        plan.clear();
        orders.forEach(o -> plan.put(o.getId(), o));
        return transfers();
    }

    public synchronized List<TransferView> transfers() {
        return plan.values().stream().map(NetworkService::view).toList();
    }

    public synchronized TransferView approve(String transferId) throws RaktSetuException {
        TransferOrder order = order(transferId);
        transfers.approve(order, today);
        save();
        return view(order);
    }

    public synchronized TransferView reject(String transferId) throws RaktSetuException {
        TransferOrder order = order(transferId);
        if (order.getStatus() != TransferStatus.PROPOSED) {
            throw new RaktSetuException("Transfer " + transferId + " is already " + order.getStatus());
        }
        transfers.reject(order);
        return view(order);
    }

    // ------------------------------------------------------------------ audit, simulation, date

    public synchronized AuditView audit(int limit) throws RaktSetuException {
        List<AuditRecord> records = auditLog.readAll();
        int from = Math.max(0, records.size() - Math.max(1, limit));
        List<AuditEntry> entries = new ArrayList<>();
        for (AuditRecord r : records.subList(from, records.size()).reversed()) {
            var e = r.event();
            entries.add(new AuditEntry(r.sequence(), e.date(), e.type().name(), e.facilityId(), e.counterparty(),
                    e.component().name(), e.group().label(), e.unitIds().size(), e.note(), r.hash()));
        }
        AuditLogger.Verification v = auditLog.verify();
        return new AuditView(records.size(), v.intact(), v.message(), entries);
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
        network.removeExpired(today);
        plan.clear();
        save();
        return summary();
    }

    // ------------------------------------------------------------------ helpers

    private void save() throws RaktSetuException {
        loader.save(network.snapshot());
    }

    private TransferOrder order(String id) throws RaktSetuException {
        TransferOrder order = plan.get(id);
        if (order == null) {
            throw new RaktSetuException("No transfer with ID '" + id + "' in the current plan");
        }
        return order;
    }

    private List<TransferOrder> pendingTransfers() {
        return plan.values().stream().filter(o -> o.getStatus() == TransferStatus.PROPOSED).toList();
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

    private static TransferView view(TransferOrder o) {
        return new TransferView(o.getId(), o.getSource().getId(), o.getDestination().getId(), o.describeUnits(),
                o.unitCount(), o.getRoute().distanceKm(), o.getRoute().travelMinutes(), o.getStatus().name(),
                o.getReason());
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
