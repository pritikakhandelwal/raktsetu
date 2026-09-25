package com.raktsetu.cli;

import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.interfaces.Exportable;
import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.BloodRequest;
import com.raktsetu.domain.model.CompatibilityResult;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.NetworkData;
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
import com.raktsetu.engine.strategy.RedistributionStrategy;
import com.raktsetu.engine.strategy.StrategyComparison;
import com.raktsetu.persistence.AuditLogger;
import com.raktsetu.persistence.AuditRecord;
import com.raktsetu.persistence.DataLoader;
import com.raktsetu.persistence.ReportWriter;
import com.raktsetu.sim.SimulationResult;
import com.raktsetu.sim.Simulator;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The 16-option console. It only reads input, calls the engine and prints results; all decisions live in the engine.
 */
final class ConsoleApp {

    static final String[] MENU = {
            "View centre stock",
            "View network-wide stock",
            "Add blood units",
            "Raise a blood request",
            "Check compatibility",
            "Run the expiry-risk check",
            "Generate a redistribution plan",
            "Compare strategies",
            "Approve or reject transfers",
            "View audit log and verify hash chain",
            "Run the simulation",
            "View the wastage summary",
            "Export reports (CSV)",
            "Data-freshness report",
            "Set the date (\"today\")",
            "Save and exit",
    };

    private static final int URGENT_DAYS = 2;
    private static final int AUDIT_ROWS_SHOWN = 15;

    private final ConsoleIO io;
    private final DataLoader loader;
    private final AuditLogger auditLog;
    private final BloodNetwork network;
    private final RequestService requests;
    private final TransferService transfers;
    private final RedistributionEngine engine = RedistributionEngine.withDefaultStrategies();
    private final ReportWriter reports;
    private final long seed;
    private LocalDate today;
    private List<TransferOrder> pending = List.of();

    ConsoleApp(ConsoleIO io, DataLoader loader, AuditLogger auditLog, NetworkData data, LocalDate today, long seed) {
        this.io = io;
        this.loader = loader;
        this.auditLog = auditLog;
        this.network = new BloodNetwork(data, auditLog);
        this.requests = new RequestService(network);
        this.transfers = new TransferService(network);
        this.reports = new ReportWriter(loader.directory().resolve("reports"));
        this.today = today;
        this.seed = seed;
    }

    void run() {
        int units = network.storingFacilities().stream().mapToInt(f -> f.getInventory().totalUnits()).sum();
        io.println("Today is " + today + ". " + network.facilities().size() + " facilities, " + units
                + " units in stock.");
        removeExpiredAndReport();
        while (true) {
            printMenu();
            try {
                String line = io.optional("Choose an option (1-" + MENU.length + ")");
                int choice;
                try {
                    choice = Integer.parseInt(line);
                } catch (NumberFormatException e) {
                    choice = -1;
                }
                if (choice < 1 || choice > MENU.length) {
                    io.println("Please enter a number between 1 and " + MENU.length + ".");
                    continue;
                }
                io.println();
                io.println("===== " + MENU[choice - 1].toUpperCase(Locale.ROOT) + " =====");
                if (dispatch(choice)) {
                    return;
                }
            } catch (IllegalArgumentException e) {
                io.println("Input error: " + e.getMessage());
            } catch (RaktSetuException e) {
                io.println("Error: " + e.getMessage());
            } catch (ConsoleIO.EndOfInput e) {
                io.println();
                io.println("Input ended. Changes since the last save were not saved.");
                return;
            }
        }
    }

    private void printMenu() {
        io.println();
        io.println("===== RAKTSETU  (today: " + today + ", pending transfers: " + proposed().size() + ") =====");
        for (int i = 0; i < MENU.length; i++) {
            io.printf("%2d. %s%n", i + 1, MENU[i]);
        }
    }

    /** Runs one option; returns true to exit. */
    private boolean dispatch(int choice) throws RaktSetuException {
        switch (choice) {
            case 1 -> viewCentreStock();
            case 2 -> viewNetworkStock();
            case 3 -> addUnits();
            case 4 -> raiseRequest();
            case 5 -> checkCompatibility();
            case 6 -> riskCheck();
            case 7 -> generatePlan();
            case 8 -> compareStrategies();
            case 9 -> approveTransfers();
            case 10 -> viewAuditLog();
            case 11 -> runSimulation();
            case 12 -> wastageSummary();
            case 13 -> exportReports();
            case 14 -> freshnessReport();
            case 15 -> setDate();
            case 16 -> {
                loader.save(network.snapshot());
                io.println("Saved to " + loader.directory().toAbsolutePath() + ". Goodbye.");
                return true;
            }
            default -> throw new IllegalStateException("Unhandled option " + choice);
        }
        return false;
    }

    // ----------------------------------------------------------------- 1, 2, 3: stock

    private void viewCentreStock() throws RaktSetuException {
        Facility f = network.facility(io.text("Centre ID (" + ids(network.storingFacilities()) + ")"));
        if (!f.canStore()) {
            io.println(f + " is a hospital and holds no stock of its own.");
            return;
        }
        io.println(f.getName() + " (" + f.getId() + "), last stock report " + reportText(f));
        TextTable table = new TextTable("Component", "Group", "Units", "Soonest expiry", "Days left", "");
        for (ComponentType type : ComponentType.values()) {
            for (BloodGroup group : BloodGroup.values()) {
                List<BloodComponent> units = f.getInventory().units(type, group);
                if (!units.isEmpty()) {
                    BloodComponent first = units.get(0);
                    long left = first.daysToExpiry(today);
                    table.row(type, group, units.size(), first.getExpiryDate(), left,
                            left <= URGENT_DAYS ? "URGENT" : "");
                }
            }
        }
        if (table.isEmpty()) {
            io.println("No stock.");
        } else {
            table.print(io.out());
            io.println("Total: " + f.getInventory().totalUnits() + " units. URGENT = expires within " + URGENT_DAYS
                    + " days.");
        }
    }

    private void viewNetworkStock() {
        List<String> header = new ArrayList<>(List.of("Component"));
        for (BloodGroup g : BloodGroup.values()) {
            header.add(g.label());
        }
        header.add("Total");
        TextTable table = new TextTable(header.toArray(String[]::new));
        List<String> low = new ArrayList<>();
        for (ComponentType type : ComponentType.values()) {
            List<Object> row = new ArrayList<>(List.of(type));
            int total = 0;
            for (BloodGroup g : BloodGroup.values()) {
                int n = network.totalUsable(type, g, today);
                total += n;
                row.add(n);
                double daily = 0;
                for (Facility f : network.storingFacilities()) {
                    daily += network.usage().averageDaily(f.getId(), type, g, today);
                }
                if (daily > 0 && n < daily * 2) {
                    low.add(String.format(Locale.ROOT, "%s %s: %d units, about %.1f days of use", g, type, n,
                            n / daily));
                }
            }
            row.add(total);
            table.row(row.toArray());
        }
        table.print(io.out());
        if (low.isEmpty()) {
            io.println("No group is running low (below two days of network-wide use).");
        } else {
            io.println("Running LOW (below two days of network-wide use):");
            low.forEach(l -> io.println("  - " + l));
        }
    }

    private void addUnits() throws RaktSetuException {
        String facilityId = io.text("Centre ID (" + ids(network.storingFacilities()) + ")");
        ComponentType type = ComponentType.parse(io.text("Component (WHOLE_BLOOD, RED_CELLS, PLATELETS, PLASMA)"));
        BloodGroup group = BloodGroup.parse(io.text("Blood group (e.g. O-)"));
        LocalDate collected = io.date("Collection date", today);
        int quantity = io.integer("Number of units", 1, 500);
        List<BloodComponent> added = network.addUnits(facilityId, type, group, collected, quantity, today);
        BloodComponent first = added.get(0);
        io.println("Added " + added.size() + " x " + group + " " + type + " to " + facilityId + " (IDs "
                + first.getId() + (added.size() > 1 ? " to " + added.get(added.size() - 1).getId() : "")
                + "), expiring on " + first.getExpiryDate() + ".");
    }

    // ----------------------------------------------------------------- 4, 5: requests and compatibility

    private void raiseRequest() throws RaktSetuException {
        int mode = io.integer("Request (1) a number of units or (2) one specific unit", 1, 2, 1);
        String hospitalIds = ids(network.facilities().stream().filter(f -> !f.canStore()).toList());
        if (mode == 2) {
            String facilityId = io.text("Centre holding the unit (" + ids(network.storingFacilities()) + ")");
            String unitId = io.text("Unit ID");
            String hospitalId = io.text("Hospital ID (" + hospitalIds + ")");
            BloodGroup recipient = BloodGroup.parse(io.text("Patient's blood group"));
            Allocation a = requests.issueUnit(facilityId, unitId, hospitalId, recipient, today);
            io.println("Issued " + unitId + " (" + a.donorGroup() + ") to " + hospitalId + ": "
                    + a.compatibility().reason());
            io.println("Recorded in the audit log.");
            return;
        }
        String hospitalId = io.text("Hospital ID (" + hospitalIds + ")");
        ComponentType type = ComponentType.parse(io.text("Component (WHOLE_BLOOD, RED_CELLS, PLATELETS, PLASMA)"));
        BloodGroup recipient = BloodGroup.parse(io.text("Patient's blood group"));
        int quantity = io.integer("Units needed", 1, 50);
        String u = io.optional("Urgency: (E)mergency or (R)outine [E]").toUpperCase(Locale.ROOT);
        Urgency urgency;
        if (u.isEmpty() || u.startsWith("E")) {
            urgency = Urgency.EMERGENCY;
        } else if (u.startsWith("R")) {
            urgency = Urgency.ROUTINE;
        } else {
            throw new IllegalArgumentException("Urgency must be E (emergency) or R (routine)");
        }

        io.println("Searching centres within " + RequestService.DEFAULT_MAX_TRAVEL_MINUTES + " min of " + hospitalId
                + ", nearest first (centres with unverified data last)...");
        RequestOutcome outcome = requests.handle(new BloodRequest(hospitalId, type, recipient, quantity, urgency),
                today);
        for (Allocation a : outcome.allocations()) {
            String days = a.units().stream().map(x -> String.valueOf(x.daysToExpiry(today)))
                    .collect(Collectors.joining(", "));
            io.printf("  %s (%d km, %d min): issued %d x %s %s, expiring in %s days%n", a.source().getId(),
                    a.route().distanceKm(), a.route().travelMinutes(), a.units().size(), a.donorGroup(), type, days);
            if (a.isSubstitute()) {
                io.println("    substitute: " + a.compatibility().reason());
            }
        }
        io.println("Request fulfilled: " + outcome.unitsIssued() + " unit" + (outcome.unitsIssued() == 1 ? "" : "s")
                + " from " + outcome.allocations().stream().map(a -> a.source().getId()).distinct()
                .collect(Collectors.joining(", ")) + ". Recorded in the audit log.");
    }

    private void checkCompatibility() {
        ComponentType type = ComponentType.parse(io.text("Component (WHOLE_BLOOD, RED_CELLS, PLATELETS, PLASMA)"));
        BloodGroup donor = BloodGroup.parse(io.text("Donor blood group (e.g. O-)"));
        BloodGroup recipient = BloodGroup.parse(io.text("Recipient blood group (e.g. AB+)"));
        CompatibilityResult result = type.newUnit("CHECK", donor, today).compatibilityWith(recipient);
        io.println((result.isAllowed() ? "ALLOWED" : "NOT ALLOWED") + " (" + result.level() + "): "
                + result.reason());
    }

    // ----------------------------------------------------------------- 6-9: risk and redistribution

    private void riskCheck() {
        List<RiskItem> risks = engine.risks(network, today);
        if (risks.isEmpty()) {
            io.println("No units are at risk of expiring unused in the next week.");
            return;
        }
        int total = 0;
        for (RiskItem r : risks) {
            io.println("  - " + r.reason());
            total += r.atRisk().size();
        }
        io.println(total + " units at risk. Option 7 proposes where they could go.");
    }

    private void generatePlan() {
        List<RedistributionStrategy> strategies = engine.strategies();
        for (int i = 0; i < strategies.size(); i++) {
            io.printf("  %d. %s: %s%n", i + 1, strategies.get(i).name(), strategies.get(i).description());
        }
        int choice = io.integer("Strategy", 1, strategies.size(), 2);
        RedistributionStrategy strategy = strategies.get(choice - 1);
        pending = engine.plan(strategy.name(), network, today);
        if (pending.isEmpty()) {
            io.println("No transfers proposed: no at-risk unit has a centre that expects to use it in time.");
            return;
        }
        io.println(strategy.name() + " proposes " + pending.size() + " transfer" + (pending.size() == 1 ? "" : "s")
                + ":");
        for (TransferOrder o : pending) {
            printOrder(o);
        }
        io.println("Nothing has moved yet. Use option 9 to approve or reject.");
    }

    private void compareStrategies() {
        List<StrategyComparison> comparison = engine.compare(network, today);
        TextTable table = new TextTable("Strategy", "Transfers", "Units rescued", "Still at risk",
                "Road km (1 trip per transfer)");
        for (StrategyComparison c : comparison) {
            table.row(c.strategy(), c.orders().size(), c.unitsRescued(), c.stillAtRisk(), c.totalKm());
        }
        table.print(io.out());
        io.println("Both strategies see the same " + (comparison.isEmpty() ? 0 : comparison.get(0).atRiskUnits())
                + " at-risk units; they differ in where they send them.");
    }

    private void approveTransfers() {
        List<TransferOrder> open = proposed();
        if (open.isEmpty()) {
            io.println("No transfers are waiting. Use option 7 to generate a plan.");
            return;
        }
        for (TransferOrder o : open) {
            printOrder(o);
            while (true) {
                String answer = io.optional("[a]pprove, [r]eject, [s]kip or [q]uit").toLowerCase(Locale.ROOT);
                if (answer.startsWith("a")) {
                    try {
                        transfers.approve(o, today);
                        io.println("    Approved: " + o.unitCount() + (o.unitCount() == 1 ? " unit" : " units")
                                + " moved to " + o.getDestination().getId()
                                + " and recorded in the audit log.");
                    } catch (RaktSetuException e) {
                        io.println("    Could not approve: " + e.getMessage());
                    }
                    break;
                } else if (answer.startsWith("r")) {
                    transfers.reject(o);
                    io.println("    Rejected.");
                    break;
                } else if (answer.startsWith("s")) {
                    break;
                } else if (answer.startsWith("q")) {
                    return;
                }
                io.println("    Please type a, r, s or q.");
            }
        }
    }

    private void printOrder(TransferOrder o) {
        io.printf("  %s: %s  %s -> %s (%d km, %d min) [%s]%n", o.getId(), o.describeUnits(), o.getSource().getId(),
                o.getDestination().getId(), o.getRoute().distanceKm(), o.getRoute().travelMinutes(), o.getStatus());
        io.println("    why: " + o.getReason());
    }

    private List<TransferOrder> proposed() {
        return pending.stream().filter(o -> o.getStatus() == TransferStatus.PROPOSED).toList();
    }

    // ----------------------------------------------------------------- 10-13: audit, simulation, reports

    private void viewAuditLog() throws RaktSetuException {
        List<AuditRecord> records = auditLog.readAll();
        if (records.isEmpty()) {
            io.println("The audit log is empty.");
        } else {
            List<AuditRecord> shown = records.subList(Math.max(0, records.size() - AUDIT_ROWS_SHOWN), records.size());
            io.println("Last " + shown.size() + " of " + records.size() + " entries (" + auditLog.file() + "):");
            TextTable table = new TextTable("#", "Date", "Event", "At", "To", "Units", "Kind", "Note");
            for (AuditRecord r : shown) {
                var e = r.event();
                String note = e.note().length() > 48 ? e.note().substring(0, 45) + "..." : e.note();
                table.row(r.sequence(), e.date(), e.type(), e.facilityId(), e.counterparty(), e.unitIds().size(),
                        e.group() + " " + e.component(), note);
            }
            table.print(io.out());
        }
        io.println(auditLog.verify().message());
    }

    private void runSimulation() throws RaktSetuException {
        int days = io.integer("Days to simulate", 1, 365, Simulator.DEFAULT_DAYS);
        io.println("Simulating " + days + " days from " + today + " on a fresh copy of the sample network (seed "
                + seed + "). Every policy sees the same supply and demand; your data is not changed...");
        List<SimulationResult> results = new Simulator(today, days, seed).compareAll();
        SimulationResult base = results.get(0);
        List<String> header = new ArrayList<>(List.of("Measure"));
        results.forEach(r -> header.add(r.policy()));
        TextTable table = new TextTable(header.toArray(String[]::new));
        addSimRow(table, "Units expired", results, SimulationResult::unitsExpired);
        addSimRow(table, "  platelets", results, r -> r.expired(ComponentType.PLATELETS));
        addSimRow(table, "  red cells", results, r -> r.expired(ComponentType.RED_CELLS));
        addSimRow(table, "  whole blood", results, r -> r.expired(ComponentType.WHOLE_BLOOD));
        addSimRow(table, "  plasma", results, r -> r.expired(ComponentType.PLASMA));
        addSimRow(table, "Requests fulfilled", results, SimulationResult::requestsFulfilled);
        addSimRow(table, "Requests unmet", results, SimulationResult::requestsUnmet);
        addSimRow(table, "Units transferred", results, SimulationResult::unitsTransferred);
        table.print(io.out());
        for (SimulationResult r : results.subList(1, results.size())) {
            io.printf("Wastage reduced by %.1f%% with %s.%n", r.wastageReductionVersus(base), r.policy());
        }
        Path file = reports.write("simulation.csv", SimulationResult.CSV_HEADER, results);
        io.println("Saved to " + file.toAbsolutePath());
    }

    private static void addSimRow(TextTable table, String label, List<SimulationResult> results,
                                  java.util.function.ToIntFunction<SimulationResult> value) {
        List<Object> row = new ArrayList<>(List.of(label));
        results.forEach(r -> row.add(value.applyAsInt(r)));
        table.row(row.toArray());
    }

    private void wastageSummary() {
        List<WastageRecord> wastage = network.wastage();
        if (wastage.isEmpty()) {
            io.println("No units have expired yet.");
            return;
        }
        Map<ComponentType, Integer> byComponent = new EnumMap<>(ComponentType.class);
        Map<String, Integer> byFacility = new TreeMap<>();
        for (WastageRecord w : wastage) {
            byComponent.merge(w.component(), 1, Integer::sum);
            byFacility.merge(w.facilityId(), 1, Integer::sum);
        }
        TextTable table = new TextTable("Component", "Units expired", "Share");
        byComponent.forEach((type, n) -> table.row(type, n,
                String.format(Locale.ROOT, "%.0f%%", 100.0 * n / wastage.size())));
        table.print(io.out());
        io.println("By centre: " + byFacility.entrySet().stream().map(e -> e.getKey() + " " + e.getValue())
                .collect(Collectors.joining(", ")) + ". Total " + wastage.size() + " units.");
    }

    private void exportReports() throws RaktSetuException {
        List<StockRow> stock = new ArrayList<>();
        for (Facility f : network.storingFacilities()) {
            for (ComponentType type : ComponentType.values()) {
                for (BloodGroup g : BloodGroup.values()) {
                    List<BloodComponent> units = f.getInventory().units(type, g);
                    if (!units.isEmpty()) {
                        stock.add(new StockRow(f.getId(), type, g, units.size(), units.get(0).getExpiryDate()));
                    }
                }
            }
        }
        List<Path> written = new ArrayList<>();
        written.add(reports.write("stock.csv", StockRow.HEADER, stock));
        written.add(reports.write("risk.csv", List.of("date", "facility_id", "component", "blood_group",
                "at_risk_units", "usable_stock", "soonest_days_left", "avg_daily_usage"),
                engine.risks(network, today)));
        written.add(reports.write("transfer_plan.csv", List.of("id", "planned_on", "from_id", "to_id", "units",
                "distance_km", "travel_minutes", "status", "reason"), pending));
        written.add(reports.write("wastage.csv", List.of("date", "facility_id", "unit_id", "component",
                "blood_group"), network.wastage()));
        written.add(reports.write("freshness.csv", FreshnessRow.HEADER, freshnessRows()));
        io.println("Exported " + written.size() + " reports to " + written.get(0).getParent().toAbsolutePath() + ":");
        written.forEach(p -> io.println("  " + p.getFileName()));
    }

    // ----------------------------------------------------------------- 14, 15: freshness and date

    private void freshnessReport() throws RaktSetuException {
        TextTable table = new TextTable("Facility", "Type", "Last stock report", "Days ago", "Status");
        for (FreshnessRow r : freshnessRows()) {
            table.row(r.facilityId(), r.type(), r.lastReport(), r.daysAgo(), r.status());
        }
        table.print(io.out());
        io.println("FRESH = reported within " + Freshness.FRESH_DAYS + " day; STALE = within " + Freshness.STALE_DAYS
                + " days; UNVERIFIED = older. Unverified centres are searched last and never sent transfers.");
        String id = io.optional("Record a stock report today for centre (ID, or blank to skip)");
        if (!id.isEmpty()) {
            Facility f = network.facility(id);
            f.markReported(today);
            io.println(f.getId() + " marked as reported on " + today + ".");
        }
    }

    private List<FreshnessRow> freshnessRows() {
        List<FreshnessRow> rows = new ArrayList<>();
        for (Facility f : network.storingFacilities()) {
            long days = f.daysSinceReport(today);
            rows.add(new FreshnessRow(f.getId(), f.getFacilityType().name(),
                    f.getLastReportedOn() == null ? "never" : f.getLastReportedOn().toString(),
                    days == Long.MAX_VALUE ? "-" : String.valueOf(days), Freshness.of(f, today).name()));
        }
        return rows;
    }

    private void setDate() {
        LocalDate next = ConsoleIO.parseDate(io.text("New date (YYYY-MM-DD)"));
        if (next.isBefore(today)) {
            throw new IllegalArgumentException("The date can only move forward; stock has already been processed up to "
                    + today);
        }
        today = next;
        io.println("Today is now " + today + ".");
        removeExpiredAndReport();
    }

    private void removeExpiredAndReport() {
        List<WastageRecord> removed = network.removeExpired(today);
        if (!removed.isEmpty()) {
            io.println(removed.size() + " expired unit" + (removed.size() == 1 ? " was" : "s were")
                    + " removed from stock and recorded as wastage (see option 12).");
        }
    }

    private String reportText(Facility f) {
        return (f.getLastReportedOn() == null ? "never" : f.getLastReportedOn().toString()) + " ["
                + Freshness.of(f, today) + "]";
    }

    private static String ids(List<Facility> facilities) {
        return facilities.stream().map(Facility::getId).collect(Collectors.joining(", "));
    }

    /** One row of the stock report. */
    record StockRow(String facilityId, ComponentType component, BloodGroup group, int units, LocalDate soonestExpiry)
            implements Exportable {
        static final List<String> HEADER = List.of("facility_id", "component", "blood_group", "units",
                "soonest_expiry");

        @Override
        public List<String> toCsvRow() {
            return List.of(facilityId, component.name(), group.label(), String.valueOf(units),
                    soonestExpiry.toString());
        }
    }

    /** One row of the freshness report. */
    record FreshnessRow(String facilityId, String type, String lastReport, String daysAgo, String status)
            implements Exportable {
        static final List<String> HEADER = List.of("facility_id", "type", "last_report", "days_ago", "status");

        @Override
        public List<String> toCsvRow() {
            return List.of(facilityId, type, lastReport, daysAgo, status);
        }
    }
}
