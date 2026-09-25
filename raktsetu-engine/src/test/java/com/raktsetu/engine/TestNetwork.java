package com.raktsetu.engine;

import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.Hospital;
import com.raktsetu.domain.model.HubBloodCentre;
import com.raktsetu.domain.model.NetworkData;
import com.raktsetu.domain.model.Route;
import com.raktsetu.domain.model.RouteEntry;
import com.raktsetu.domain.model.StorageCentre;
import com.raktsetu.domain.model.UsageHistory;

import java.time.LocalDate;
import java.util.List;

/**
 * A small network for engine tests.
 *
 * <pre>
 *            10 min           30 min            45 min            200 min (out of range)
 *   H01 ---------- SC01, ---------- HUB01, ---------- SC02, ---------- SC03
 *   (travel times are from each centre to hospital H01; centre-to-centre times are listed in the constructor)
 * </pre>
 */
final class TestNetwork {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    final HubBloodCentre hub = new HubBloodCentre("HUB01", "Hub", TODAY);
    final StorageCentre sc01 = new StorageCentre("SC01", "Near centre", TODAY);
    final StorageCentre sc02 = new StorageCentre("SC02", "Far centre", TODAY);
    final StorageCentre sc03 = new StorageCentre("SC03", "Remote centre", TODAY);
    final Hospital h01 = new Hospital("H01", "City Hospital", TODAY);
    final UsageHistory usage = new UsageHistory();
    final InMemoryAuditTrail audit = new InMemoryAuditTrail();
    final BloodNetwork network;
    private int serial = 1;

    TestNetwork() {
        List<RouteEntry> routes = List.of(
                route("SC01", "H01", 5, 10), route("HUB01", "H01", 15, 30), route("SC02", "H01", 25, 45),
                route("SC03", "H01", 120, 200),
                route("HUB01", "SC01", 10, 20), route("HUB01", "SC02", 15, 30), route("SC01", "SC02", 18, 35),
                route("SC03", "HUB01", 100, 150), route("SC03", "SC01", 105, 160), route("SC03", "SC02", 90, 140));
        network = new BloodNetwork(new NetworkData(List.<Facility>of(hub, sc01, sc02, sc03, h01), routes, usage,
                List.of(), 1000), audit);
    }

    private static RouteEntry route(String a, String b, int km, int minutes) {
        return new RouteEntry(a, b, new Route(km, minutes));
    }

    /** Adds {@code count} units collected {@code daysAgo} days before {@link #TODAY}. */
    List<BloodComponent> add(Facility f, ComponentType type, BloodGroup group, int daysAgo, int count) {
        List<BloodComponent> units = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            BloodComponent u = type.newUnit(String.format("T-%04d", serial++), group, TODAY.minusDays(daysAgo));
            f.getInventory().add(u);
            units.add(u);
        }
        return units;
    }

    /** Records the same usage on each of the last 14 days. */
    void usagePerDay(Facility f, ComponentType type, BloodGroup group, int unitsPerDay) {
        for (int d = 1; d <= UsageHistory.DEFAULT_WINDOW_DAYS; d++) {
            usage.record(TODAY.minusDays(d), f.getId(), type, group, unitsPerDay);
        }
    }
}
