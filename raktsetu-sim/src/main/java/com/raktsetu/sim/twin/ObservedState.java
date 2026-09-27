package com.raktsetu.sim.twin;

import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.NetworkData;
import com.raktsetu.domain.model.RouteEntry;
import com.raktsetu.domain.model.UsageHistory;
import com.raktsetu.engine.BloodNetwork;
import com.raktsetu.engine.InMemoryAuditTrail;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the network-level planner knows: each centre's stock as of its last report, and its usage up to that report.
 * The true stock is hidden from the planner. With full compliance this equals the truth; with low compliance it can be
 * days out of date, which is the situation the research studies.
 */
public final class ObservedState {

    private record Snapshot(LocalDate reportedOn, List<BloodComponent> units) {
    }

    private final Map<String, Snapshot> snapshots = new HashMap<>();
    private final UsageHistory reportedUsage = new UsageHistory();

    /**
     * Records a report from one centre: its current true stock, and its usage since its previous report.
     *
     * @param today the report date; usage up to but excluding today is included
     */
    public void report(Facility trueCentre, UsageHistory trueUsage, LocalDate today) {
        Snapshot previous = snapshots.get(trueCentre.getId());
        LocalDate from = previous == null ? LocalDate.MIN : previous.reportedOn();
        for (UsageHistory.Entry e : trueUsage.entriesFor(trueCentre.getId(), from, today)) {
            reportedUsage.record(e.date(), e.facilityId(), e.component(), e.group(), e.units());
        }
        snapshots.put(trueCentre.getId(), new Snapshot(today, trueCentre.getInventory().allUnits()));
    }

    /**
     * The network as the planner sees it today: new facility objects holding the reported units, with each centre's
     * last report date (so data freshness works as in the live system). Units are shared, immutable objects; the real
     * stock is never touched through this view.
     */
    public BloodNetwork view(List<Facility> trueFacilities, List<RouteEntry> routes) {
        List<Facility> facilities = new ArrayList<>();
        for (Facility f : trueFacilities) {
            Snapshot s = snapshots.get(f.getId());
            Facility copy = f.getFacilityType().newFacility(f.getId(), f.getName(),
                    s == null ? null : s.reportedOn());
            if (copy.canStore() && s != null) {
                for (BloodComponent u : s.units()) {
                    copy.getInventory().add(u);
                }
            }
            facilities.add(copy);
        }
        return new BloodNetwork(new NetworkData(facilities, routes, reportedUsage, List.of(), 1),
                new InMemoryAuditTrail());
    }
}
