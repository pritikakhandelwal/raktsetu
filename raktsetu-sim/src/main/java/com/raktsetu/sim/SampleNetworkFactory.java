package com.raktsetu.sim;

import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.FacilityType;
import com.raktsetu.domain.model.NetworkData;
import com.raktsetu.domain.model.Route;
import com.raktsetu.domain.model.RouteEntry;
import com.raktsetu.domain.model.UnitIds;
import com.raktsetu.domain.model.UsageHistory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the synthetic sample network used by the demo and the simulator: one hub, four storage centres and six
 * hospitals around Pune, with stock and 14 days of usage history generated from a seed, <em>relative to today</em>,
 * so the data never goes stale.
 *
 * <p>The layout is designed to show the problem: the remote Baramati centre receives the same routine supply as the
 * others but serves one small hospital, so its short-lived stock expires unless it is redistributed. All names and
 * numbers are illustrative, not real facilities or real stock.
 */
public final class SampleNetworkFactory {

    /** A facility in the sample, with its position on a flat map in kilometres and its relative demand. */
    record Site(String id, String name, FacilityType type, double x, double y, double demand) {
    }

    static final List<Site> SITES = List.of(
            new Site("HUB01", "Hub Blood Centre, Pune (sample)", FacilityType.HUB, 0, 0, 1.2),
            new Site("SC01", "Storage Centre, Shivajinagar (sample)", FacilityType.STORAGE, 4, 3, 1.0),
            new Site("SC02", "Storage Centre, Hadapsar (sample)", FacilityType.STORAGE, 8, -6, 0.8),
            new Site("SC03", "Storage Centre, Pimpri (sample)", FacilityType.STORAGE, -14, 12, 0.8),
            new Site("SC04", "Storage Centre, Baramati (sample)", FacilityType.STORAGE, 60, -40, 0.15),
            new Site("H01", "City Hospital, Kothrud (sample)", FacilityType.HOSPITAL, 5, 5, 1.0),
            new Site("H02", "General Hospital, Camp (sample)", FacilityType.HOSPITAL, 2, -2, 1.2),
            new Site("H03", "Care Hospital, Hadapsar (sample)", FacilityType.HOSPITAL, 10, -8, 0.8),
            new Site("H04", "Community Hospital, Pimpri (sample)", FacilityType.HOSPITAL, -15, 14, 0.8),
            new Site("H05", "Trauma Centre, Aundh (sample)", FacilityType.HOSPITAL, -9, 8, 0.6),
            new Site("H06", "Rural Hospital, Baramati (sample)", FacilityType.HOSPITAL, 58, -38, 0.25));

    /** Daily demand per unit of hospital demand weight, by component. Illustrative. */
    static final Map<ComponentType, Double> DAILY_DEMAND = new EnumMap<>(Map.of(
            ComponentType.RED_CELLS, 6.0, ComponentType.PLATELETS, 2.5, ComponentType.PLASMA, 3.0,
            ComponentType.WHOLE_BLOOD, 0.5));

    private SampleNetworkFactory() {
    }

    /** Road distance from straight-line distance, with a detour factor. */
    static Route routeBetween(Site a, Site b) {
        double straight = Math.hypot(a.x() - b.x(), a.y() - b.y());
        int km = (int) Math.round(straight * 1.35) + 1;
        int minutes = (int) Math.round(km * 2.2) + 8; // about 27 km/h in city traffic, plus loading time
        return new Route(km, minutes);
    }

    public static NetworkData create(LocalDate today, long seed) {
        RandomSource random = new RandomSource(seed);
        Map<String, Facility> facilities = new LinkedHashMap<>();
        for (Site s : SITES) {
            facilities.put(s.id(), s.type().newFacility(s.id(), s.name(), today));
        }
        // Data freshness varies, as it does in practice.
        facilities.get("SC02").markReported(today.minusDays(2));
        facilities.get("SC03").markReported(today.minusDays(5));

        List<RouteEntry> routes = new ArrayList<>();
        for (int i = 0; i < SITES.size(); i++) {
            for (int j = i + 1; j < SITES.size(); j++) {
                routes.add(new RouteEntry(SITES.get(i).id(), SITES.get(j).id(), routeBetween(SITES.get(i),
                        SITES.get(j))));
            }
        }

        long[] serial = {1};
        for (Site s : SITES) {
            if (s.type() == FacilityType.HOSPITAL) {
                continue;
            }
            Facility f = facilities.get(s.id());
            int scale = s.id().equals("HUB01") ? 2 : 1;
            addRandomStock(f, ComponentType.RED_CELLS, 30 * scale, today, random, serial);
            addRandomStock(f, ComponentType.PLATELETS, 4 * scale, today, random, serial);
            addRandomStock(f, ComponentType.PLASMA, 15 * scale, today, random, serial);
            addRandomStock(f, ComponentType.WHOLE_BLOOD, 3 * scale, today, random, serial);
        }
        // The story for the demo: the remote centre holds platelets it cannot use in time...
        addUnits(facilities.get("SC04"), ComponentType.PLATELETS, BloodGroup.B_POS, today.minusDays(3), 8, serial);
        addUnits(facilities.get("SC04"), ComponentType.PLATELETS, BloodGroup.O_POS, today.minusDays(3), 6, serial);
        // ...and a few units expired overnight and will be removed at start-up.
        addUnits(facilities.get("SC02"), ComponentType.PLATELETS, BloodGroup.A_POS, today.minusDays(5), 3, serial);

        UsageHistory usage = new UsageHistory();
        for (int d = 1; d <= UsageHistory.DEFAULT_WINDOW_DAYS; d++) {
            LocalDate day = today.minusDays(d);
            for (Site s : SITES) {
                if (s.type() == FacilityType.HOSPITAL) {
                    continue;
                }
                for (ComponentType type : ComponentType.values()) {
                    for (BloodGroup g : BloodGroup.values()) {
                        double mean = DAILY_DEMAND.get(type) * s.demand() * RandomSource.GROUP_SHARE.get(g);
                        usage.record(day, s.id(), type, g, random.poisson(mean));
                    }
                }
            }
        }
        return new NetworkData(new ArrayList<>(facilities.values()), routes, usage, List.of(), serial[0]);
    }

    private static void addRandomStock(Facility f, ComponentType type, int count, LocalDate today,
                                       RandomSource random, long[] serial) {
        for (int i = 0; i < count; i++) {
            BloodComponent unit = type.newUnit(UnitIds.format(serial[0]++), random.bloodGroup(),
                    today.minusDays(random.usableAge(type)));
            f.getInventory().add(unit);
        }
    }

    private static void addUnits(Facility f, ComponentType type, BloodGroup group, LocalDate collectedOn, int count,
                                 long[] serial) {
        for (int i = 0; i < count; i++) {
            f.getInventory().add(type.newUnit(UnitIds.format(serial[0]++), group, collectedOn));
        }
    }
}
