package com.raktsetu.server;

import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.model.AuditEvent;
import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.CompatibilityResult;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.FacilityType;
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
import com.raktsetu.server.ApiModels.AddedUnits;
import com.raktsetu.server.ApiModels.AuditEntry;
import com.raktsetu.server.ApiModels.AuditView;
import com.raktsetu.server.ApiModels.CompatibilityInput;
import com.raktsetu.server.ApiModels.CompatibilityView;
import com.raktsetu.server.ApiModels.ComparisonView;
import com.raktsetu.server.ApiModels.FacilityInput;
import com.raktsetu.server.ApiModels.FacilityStock;
import com.raktsetu.server.ApiModels.FacilityView;
import com.raktsetu.server.ApiModels.NetworkStock;
import com.raktsetu.server.ApiModels.NetworkStockRow;
import com.raktsetu.server.ApiModels.RiskView;
import com.raktsetu.server.ApiModels.RouteInput;
import com.raktsetu.server.ApiModels.RouteView;
import com.raktsetu.server.ApiModels.StockLine;
import com.raktsetu.server.ApiModels.StrategyView;
import com.raktsetu.server.ApiModels.Summary;
import com.raktsetu.server.ApiModels.WastageView;
import com.raktsetu.server.db.AuditRepository;
import com.raktsetu.server.db.NetworkRepository;
import com.raktsetu.server.db.NetworkRepository.Location;
import com.raktsetu.server.db.NetworkRepository.RouteRow;
import com.raktsetu.server.db.NotificationRepository;
import com.raktsetu.server.db.NotificationRepository.Kind;
import com.raktsetu.server.db.StateRepository;
import com.raktsetu.server.db.TransferRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The network: stock, facilities and routes, and everything computed from them.
 *
 * <p>The engine works on an in-memory network; the database is the source of truth. Every change runs through
 * {@link #change}: the change's audit events are appended to the hash-chained audit log and applied to the tables in
 * one transaction. If writing fails, the in-memory network is reloaded from the database, so the two never drift
 * apart. One network is shared by all users of this server, so every method runs under {@link NetworkLock}: reads
 * in parallel, changes one at a time.
 *
 * <p>"Today" is the real date in the network's time zone. When a new day starts (checked at midnight and on every
 * call), expired units are discarded and the day's reminders are sent.
 */
@Service
public class NetworkService {

    private static final int URGENT_DAYS = 2;
    private static final int MAX_UNITS_PER_ENTRY = 500;

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
    private final NotificationRepository notifications;
    private final RouteService routing;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final AuditIntegrity integrity;
    private final Clock clock;
    private final CapturingAuditTrail capture = new CapturingAuditTrail();
    private final RedistributionEngine engine = RedistributionEngine.withDefaultStrategies();
    private BloodNetwork network;
    private RequestService requests;
    private TransferService transfers;
    private volatile LocalDate today;
    private final NetworkLock lock = new NetworkLock();

    public NetworkService(NetworkRepository networkRepo, AuditRepository auditRepo, StateRepository state,
                          TransferRepository transferRepo, NotificationRepository notifications, RouteService routing,
                          JdbcClient jdbc, TransactionTemplate tx, Clock clock, AuditIntegrity integrity) {
        this.networkRepo = networkRepo;
        this.integrity = integrity;
        this.auditRepo = auditRepo;
        this.state = state;
        this.transferRepo = transferRepo;
        this.notifications = notifications;
        this.routing = routing;
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
        reload();
        newDayIfNeeded();
    }

    // ------------------------------------------------------------------ the day

    /** Runs just after midnight so expiry and reminders happen even if nobody is using the site. */
    @Scheduled(cron = "0 1 0 * * *", zone = "${raktsetu.time-zone:Asia/Kolkata}")
    public void midnight() {
        newDayIfNeeded();
    }

    /** If the date has changed: discard expired units, then send the day's reminders (once per day). */
    void newDayIfNeeded() {
        if (LocalDate.now(clock).equals(today)) {
            return; // the usual case, decided without taking any lock
        }
        lock.writeSafely(() -> {
            LocalDate now = LocalDate.now(clock);
            if (now.equals(today)) {
                return null; // another thread rolled the day over first
            }
            today = now;
            change(() -> network.removeExpired(today));
            dailyDigest();
            return null;
        });
    }

    /**
     * Once per day: tells each centre about stock expiring within two days and overdue stock reports, and tells
     * administrators which centres' data cannot be trusted and how much stock is at risk.
     */
    private void dailyDigest() {
        if (state.get(StateRepository.LAST_DIGEST).map(today.toString()::equals).orElse(false)) {
            return;
        }
        List<String> unverified = new ArrayList<>();
        int atRisk = engine.risks(network, today).stream().mapToInt(r -> r.atRisk().size()).sum();
        tx.executeWithoutResult(s -> {
            for (Facility f : network.storingFacilities()) {
                int urgent = urgentUnits(f);
                if (urgent > 0) {
                    notifications.add(today, f.getId(), Kind.EXPIRING_SOON, urgent + " unit(s) expire within 2 days. "
                            + "Issue them first, or they may be offered to other centres.");
                }
                if (Freshness.of(f, today) == Freshness.UNVERIFIED) {
                    unverified.add(f.getId());
                    notifications.add(today, f.getId(), Kind.REPORT_OVERDUE, "Your stock was last reported on "
                            + (f.getLastReportedOn() == null ? "never" : f.getLastReportedOn())
                            + ". Please report today's stock so the network can rely on it.");
                }
            }
            if (!unverified.isEmpty()) {
                notifications.add(today, null, Kind.REPORT_OVERDUE, "Stock data is unverified (over 3 days old) for "
                        + String.join(", ", unverified) + ". Their stock is searched last and receives no transfers.");
            }
            if (atRisk > 0) {
                notifications.add(today, null, Kind.EXPIRING_SOON, atRisk + " unit(s) are at risk of expiring unused "
                        + "this week. Generate a transfer plan to rescue them.");
            }
            state.put(StateRepository.LAST_DIGEST, today.toString());
        });
    }

    // ------------------------------------------------------------------ overview

    public Summary summary() {
        newDayIfNeeded();
        return lock.readSafely(() -> {
            int units = 0;
            int urgent = 0;
            for (Facility f : network.storingFacilities()) {
                units += f.getInventory().totalUnits();
                urgent += urgentUnits(f);
            }
            int atRisk = engine.risks(network, today).stream().mapToInt(r -> r.atRisk().size()).sum();
            int hospitals = network.facilities().size() - network.storingFacilities().size();
            return new Summary(today, network.storingFacilities().size(), hospitals, units, urgent, atRisk,
                    network.wastage().size(), transferRepo.countProposed(), integrity.current().intact());
        });
    }

    public List<FacilityView> facilities() {
        newDayIfNeeded();
        return lock.readSafely(() -> {
            Map<String, Integer> atRisk = atRiskByFacility();
            Map<String, Location> locations = networkRepo.locations();
            return network.facilities().stream().map(f -> view(f, atRisk, locations)).toList();
        });
    }

    public FacilityStock facilityStock(String id) throws RaktSetuException {
        newDayIfNeeded();
        return lock.read(() -> {
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
            return new FacilityStock(view(f, atRiskByFacility(), networkRepo.locations()), lines);
        });
    }

    public NetworkStock networkStock() {
        newDayIfNeeded();
        return lock.readSafely(() -> {
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
        });
    }

    public WastageView wastage() {
        newDayIfNeeded();
        return lock.readSafely(() -> {
            Map<String, Integer> byComponent = new TreeMap<>();
            Map<String, Integer> byFacility = new TreeMap<>();
            for (WastageRecord w : network.wastage()) {
                byComponent.merge(w.component().name(), 1, Integer::sum);
                byFacility.merge(w.facilityId(), 1, Integer::sum);
            }
            return new WastageView(network.wastage().size(), byComponent, byFacility);
        });
    }

    // ------------------------------------------------------------------ stock at a centre

    /**
     * Records units received at a centre. With {@code unitId} (the bag's own donation number) exactly one unit is
     * added under that ID; otherwise RaktSetu assigns IDs.
     */
    public AddedUnits addUnits(String facilityId, AddUnitsInput input) throws RaktSetuException {
        newDayIfNeeded();
        return lock.write(() -> {
            require(input, "request body");
            ComponentType type = ComponentType.parse(input.component());
            BloodGroup group = BloodGroup.parse(input.group());
            LocalDate collected = input.collectedOn() == null ? today : input.collectedOn();
            String unitId = input.unitId() == null ? "" : input.unitId().trim();
            List<BloodComponent> added;
            if (!unitId.isEmpty()) {
                if (input.quantity() != null && input.quantity() != 1) {
                    throw new IllegalArgumentException("A donation number identifies one unit; log units one at a time");
                }
                if (unitIdUsedBefore(unitId)) {
                    throw new IllegalArgumentException("Unit " + unitId + " has already been recorded in RaktSetu");
                }
                added = List.of(change(() -> network.addUnit(facilityId, unitId, type, group, collected, today)));
            } else {
                int quantity = requirePositive(input.quantity(), "quantity", MAX_UNITS_PER_ENTRY);
                added = change(() -> network.addUnits(facilityId, type, group, collected, quantity, today));
            }
            return new AddedUnits(added.stream().map(BloodComponent::getId).toList(), added.get(0).getExpiryDate());
        });
    }

    private boolean unitIdUsedBefore(String unitId) {
        return jdbc.sql("select (select count(*) from unit where id = :id) + (select count(*) from request_unit "
                        + "where unit_id = :id) + (select count(*) from wastage where unit_id = :id)")
                .param("id", unitId).query(Integer.class).single() > 0;
    }

    public FacilityView recordReport(String facilityId) throws RaktSetuException {
        newDayIfNeeded();
        return lock.write(() -> {
            Facility f = change(() -> {
                Facility facility = network.facility(facilityId);
                facility.markReported(today);
                return facility;
            });
            return view(f, atRiskByFacility(), networkRepo.locations());
        });
    }

    public CompatibilityView compatibility(CompatibilityInput input) {
        return lock.readSafely(() -> {
            require(input, "request body");
            ComponentType type = ComponentType.parse(input.component());
            BloodGroup donor = BloodGroup.parse(input.donor());
            BloodGroup recipient = BloodGroup.parse(input.recipient());
            CompatibilityResult r = type.newUnit("CHECK", donor, LocalDate.now(clock)).compatibilityWith(recipient);
            return new CompatibilityView(r.isAllowed(), r.level().name(), r.reason());
        });
    }

    // ------------------------------------------------------------------ facilities and routes (administrators)

    /**
     * Adds a blood centre, storage centre or hospital at a location, and works out road routes to every other
     * facility.
     */
    public FacilityView addFacility(FacilityInput input) {
        newDayIfNeeded();
        return lock.writeSafely(() -> {
            Validated v = validate(input);
            String prefix = switch (v.type()) {
                case HUB -> "HUB";
                case STORAGE -> "SC";
                case HOSPITAL -> "H";
            };
            List<RouteService.Place> others = places();
            String[] id = new String[1];
            tx.executeWithoutResult(s -> {
                id[0] = networkRepo.nextFacilityId(prefix);
                networkRepo.insertFacility(id[0], v.name(), v.type(), v.latitude(), v.longitude(), v.address());
                networkRepo.replaceRoutes(id[0], legs(new RouteService.Place(id[0], v.latitude(), v.longitude()), others));
            });
            reload();
            return facilities().stream().filter(f -> f.id().equals(id[0])).findFirst().orElseThrow();
        });
    }

    /** Changes a facility's name, address or location; a new location recalculates its routes. */
    public FacilityView updateFacility(String id, FacilityInput input) throws RaktSetuException {
        newDayIfNeeded();
        return lock.write(() -> {
            Facility existing = network.facility(id);
            Validated v = validate(new FacilityInput(input == null ? null : input.name(), existing.getFacilityType().name(),
                    input == null ? null : input.latitude(), input == null ? null : input.longitude(),
                    input == null ? null : input.address()));
            Location old = networkRepo.locations().get(id);
            boolean moved = old == null || old.latitude() == null || old.latitude() != v.latitude()
                    || old.longitude() != v.longitude();
            List<RouteService.Place> others = places().stream().filter(p -> !p.id().equals(id)).toList();
            tx.executeWithoutResult(s -> {
                networkRepo.updateFacility(id, v.name(), v.latitude(), v.longitude(), v.address());
                if (moved) {
                    networkRepo.replaceRoutes(id, legs(new RouteService.Place(id, v.latitude(), v.longitude()), others));
                }
            });
            reload();
            return facilities().stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow();
        });
    }

    public List<RouteView> routes(String id) throws RaktSetuException {
        return lock.read(() -> {
            network.facility(id);
            Map<String, String> names = new HashMap<>();
            network.facilities().forEach(f -> names.put(f.getId(), f.getName()));
            return networkRepo.routesFrom(id).stream()
                    .map(r -> new RouteView(r.toId(), names.getOrDefault(r.toId(), r.toId()), r.distanceKm(),
                            r.travelMinutes(), r.source()))
                    .toList();
        });
    }

    /** Corrects a route by hand, in both directions. */
    public RouteView setRoute(String fromId, String toId, RouteInput input) throws RaktSetuException {
        newDayIfNeeded();
        return lock.write(() -> {
            network.facility(fromId);
            Facility to = network.facility(toId);
            if (fromId.equals(toId)) {
                throw new IllegalArgumentException("A route needs two different facilities");
            }
            int km = requirePositive(input == null ? null : input.distanceKm(), "distanceKm", 5000);
            int minutes = requirePositive(input == null ? null : input.travelMinutes(), "travelMinutes", 6000);
            tx.executeWithoutResult(s -> {
                networkRepo.upsertRoute(new RouteRow(fromId, toId, km, minutes, "MANUAL"));
                networkRepo.upsertRoute(new RouteRow(toId, fromId, km, minutes, "MANUAL"));
            });
            reload();
            return new RouteView(toId, to.getName(), km, minutes, "MANUAL");
        });
    }

    private record Validated(String name, FacilityType type, double latitude, double longitude, String address) {
    }

    private static Validated validate(FacilityInput input) {
        require(input, "request body");
        String name = require(input.name(), "name").trim();
        if (name.length() > 200) {
            throw new IllegalArgumentException("name is too long");
        }
        FacilityType type = FacilityType.parse(require(input.type(), "type"));
        double lat = require(input.latitude(), "latitude");
        double lon = require(input.longitude(), "longitude");
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("latitude and longitude are out of range");
        }
        String address = input.address() == null ? null : input.address().trim();
        if (address != null && address.length() > 300) {
            throw new IllegalArgumentException("address is too long");
        }
        return new Validated(name, type, lat, lon, address);
    }

    private List<RouteService.Place> places() {
        List<RouteService.Place> places = new ArrayList<>();
        networkRepo.locations().forEach((fid, loc) -> {
            if (loc.latitude() != null && loc.longitude() != null) {
                places.add(new RouteService.Place(fid, loc.latitude(), loc.longitude()));
            }
        });
        return places;
    }

    private List<RouteRow> legs(RouteService.Place place, List<RouteService.Place> others) {
        return routing.between(place, others).stream()
                .map(l -> new RouteRow(l.fromId(), l.toId(), l.distanceKm(), l.travelMinutes(), l.source()))
                .toList();
    }

    // ------------------------------------------------------------------ risk and redistribution

    public List<RiskView> risks() {
        newDayIfNeeded();
        return lock.readSafely(() -> {
            return engine.risks(network, today).stream()
                    .map(r -> new RiskView(r.facility().getId(), r.facility().getName(), r.component().name(),
                            r.group().label(), r.atRisk().size(), r.usableStock(), r.soonestDaysLeft(),
                            r.averageDaily(), r.reason()))
                    .toList();
        });
    }

    public List<StrategyView> strategies() {
        return lock.readSafely(() -> {
            return engine.strategies().stream().map(s -> new StrategyView(s.name(), s.description())).toList();
        });
    }

    public List<ComparisonView> compare() {
        newDayIfNeeded();
        return lock.readSafely(() -> {
            List<ComparisonView> result = new ArrayList<>();
            for (StrategyComparison c : engine.compare(network, today)) {
                result.add(new ComparisonView(c.strategy(), c.orders().size(), c.unitsRescued(), c.stillAtRisk(),
                        c.totalKm()));
            }
            return result;
        });
    }

    // ------------------------------------------------------------------ audit

    public AuditView audit(int limit) {
        return lock.readSafely(() -> {
            List<AuditEntry> entries = new ArrayList<>();
            for (AuditRecord r : auditRepo.latest(limit)) {
                var e = r.event();
                entries.add(new AuditEntry(r.sequence(), e.date(), e.type().name(), e.facilityId(), e.counterparty(),
                        e.component().name(), e.group().label(), e.unitIds().size(), e.note(), r.hash()));
            }
            AuditRepository.Verification v = integrity.full(); // the audit page always re-checks the whole chain
            return new AuditView(auditRepo.count(), v.intact(), v.message(), entries);
        });
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
        if (!lock.holdsWrite()) {
            throw new IllegalStateException("Changes to the network must hold the write lock");
        }
        capture.clear();
        T result = null;
        RaktSetuException refused = null;
        try {
            result = command.run();
        } catch (RaktSetuException e) {
            refused = e;
        } catch (RuntimeException e) {
            capture.clear(); // nothing was changed: engine operations check before they act
            throw e;
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

    /**
     * Reloads from the database and processes the current day again (expiry and reminders). Used after data has been
     * loaded into the database directly, for example a bulk import.
     */
    void reloadAndReprocessDay() {
        lock.writeSafely(() -> {
            reload();
            today = null;
            state.put(StateRepository.LAST_DIGEST, "");
            return null;
        });
        newDayIfNeeded();
    }

    /** Rebuilds the in-memory network from the database. */
    void reload() {
        lock.writeSafely(() -> {
            long serial = state.get(StateRepository.NEXT_UNIT_SERIAL).map(Long::parseLong).orElse(1L);
            network = new BloodNetwork(networkRepo.load(serial), capture);
            requests = new RequestService(network);
            transfers = new TransferService(network);
            return null;
        });
    }

    // ------------------------------------------------------------------ for WorkflowService (same lock)

    /** The lock guarding the network; {@link WorkflowService} uses it too. */
    NetworkLock lock() {
        return lock;
    }

    BloodNetwork network() {
        return network;
    }

    /** Today's date. Call {@link #newDayIfNeeded()} first, before taking the lock. */
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

    private FacilityView view(Facility f, Map<String, Integer> atRisk, Map<String, Location> locations) {
        long days = f.daysSinceReport(today);
        Location loc = locations.getOrDefault(f.getId(), new Location(null, null, null));
        return new FacilityView(f.getId(), f.getName(), f.getFacilityType().name(), f.getLastReportedOn(),
                days == Long.MAX_VALUE ? null : days, Freshness.of(f, today).name(),
                f.canStore() ? f.getInventory().totalUnits() : 0, urgentUnits(f), atRisk.getOrDefault(f.getId(), 0),
                loc.latitude(), loc.longitude(), loc.address());
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
