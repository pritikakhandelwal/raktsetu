package com.raktsetu.bench;

import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.FacilityType;
import com.raktsetu.domain.model.NetworkData;
import com.raktsetu.domain.model.Route;
import com.raktsetu.domain.model.RouteEntry;
import com.raktsetu.domain.model.UnitIds;
import com.raktsetu.domain.model.UsageHistory;
import com.raktsetu.sim.RandomSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a blood network of any size for benchmarks: centres spread over a square region (one centre per
 * 625 km², about the density of blood centres in Maharashtra), each with hospitals close by, stock of realistic
 * ages and 14 days of usage history.
 *
 * <p>Routes are only created between facilities within {@link #MAX_ROUTE_KM} km in a straight line, as a real road
 * network would be looked up; a complete graph would grow with the square of the size and no real system stores
 * one. Neighbours are found with a grid of cells {@code MAX_ROUTE_KM} wide, so generation stays close to linear.
 */
public final class SyntheticNetwork {

    /** Straight-line distance beyond which no route is stored. */
    public static final double MAX_ROUTE_KM = 100;

    /** Share of each component in stock. */
    private static final Map<ComponentType, Double> STOCK_MIX = new EnumMap<>(Map.of(
            ComponentType.RED_CELLS, 0.55, ComponentType.PLASMA, 0.25, ComponentType.PLATELETS, 0.12,
            ComponentType.WHOLE_BLOOD, 0.08));

    /** Average daily units used per centre, by component. */
    private static final Map<ComponentType, Double> DAILY_USE = new EnumMap<>(Map.of(
            ComponentType.RED_CELLS, 6.0, ComponentType.PLATELETS, 2.5, ComponentType.PLASMA, 3.0,
            ComponentType.WHOLE_BLOOD, 0.5));

    /**
     * What to generate.
     *
     * @param centres            blood centres (one in ten is a hub)
     * @param hospitalsPerCentre hospitals placed within 15 km of each centre
     * @param unitsPerCentre     units in stock at each centre
     * @param seed               same seed, same network
     */
    public record Spec(int centres, int hospitalsPerCentre, int unitsPerCentre, long seed) {
        public Spec {
            if (centres < 1 || hospitalsPerCentre < 0 || unitsPerCentre < 0) {
                throw new IllegalArgumentException("sizes must be positive");
            }
        }

        public static Spec ofCentres(int centres) {
            return new Spec(centres, 2, 300, 42);
        }
    }

    /** A generated facility and where it is, in km on a flat map. */
    record Site(String id, FacilityType type, double x, double y) {
        double distanceTo(Site o) {
            return Math.hypot(x - o.x, y - o.y);
        }
    }

    private SyntheticNetwork() {
    }

    public static NetworkData generate(Spec spec, LocalDate today) {
        RandomSource random = new RandomSource(spec.seed());
        double side = Math.sqrt(spec.centres()) * 25;
        List<Site> sites = new ArrayList<>();
        List<Site> centres = new ArrayList<>();
        for (int c = 1; c <= spec.centres(); c++) {
            FacilityType type = c % 10 == 1 ? FacilityType.HUB : FacilityType.STORAGE;
            Site centre = new Site(String.format("%s%04d", type == FacilityType.HUB ? "HUB" : "SC", c), type,
                    random.nextDouble() * side, random.nextDouble() * side);
            sites.add(centre);
            centres.add(centre);
            for (int h = 1; h <= spec.hospitalsPerCentre(); h++) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double r = 2 + random.nextDouble() * 13;
                sites.add(new Site(String.format("H%04d%02d", c, h), FacilityType.HOSPITAL,
                        centre.x() + r * Math.cos(angle), centre.y() + r * Math.sin(angle)));
            }
        }

        List<Facility> facilities = new ArrayList<>(sites.size());
        for (Site s : sites) {
            facilities.add(s.type().newFacility(s.id(), s.id(), today.minusDays(random.nextInt(3))));
        }

        long[] serial = {1};
        Map<String, Facility> byId = new HashMap<>();
        facilities.forEach(f -> byId.put(f.getId(), f));
        for (Site c : centres) {
            Facility f = byId.get(c.id());
            for (int i = 0; i < spec.unitsPerCentre(); i++) {
                ComponentType type = pick(random);
                f.getInventory().add(type.newUnit(UnitIds.format(serial[0]++), random.bloodGroup(),
                        today.minusDays(random.usableAge(type))));
            }
        }

        UsageHistory usage = new UsageHistory();
        for (int d = 1; d <= UsageHistory.DEFAULT_WINDOW_DAYS; d++) {
            LocalDate day = today.minusDays(d);
            for (Site c : centres) {
                for (ComponentType type : ComponentType.values()) {
                    for (BloodGroup g : BloodGroup.values()) {
                        usage.record(day, c.id(), type, g,
                                random.poisson(DAILY_USE.get(type) * RandomSource.GROUP_SHARE.get(g)));
                    }
                }
            }
        }
        return new NetworkData(facilities, routes(sites), usage, List.of(), serial[0]);
    }

    /** Routes between nearby facilities where at least one end stores blood; hospital-to-hospital is never used. */
    static List<RouteEntry> routes(List<Site> sites) {
        Map<Long, List<Site>> grid = new HashMap<>();
        for (Site s : sites) {
            grid.computeIfAbsent(cell(s.x(), s.y()), k -> new ArrayList<>()).add(s);
        }
        List<RouteEntry> routes = new ArrayList<>();
        for (Site a : sites) {
            if (a.type() == FacilityType.HOSPITAL) {
                continue;
            }
            long cx = (long) Math.floor(a.x() / MAX_ROUTE_KM);
            long cy = (long) Math.floor(a.y() / MAX_ROUTE_KM);
            for (long dx = -1; dx <= 1; dx++) {
                for (long dy = -1; dy <= 1; dy++) {
                    for (Site b : grid.getOrDefault(key(cx + dx, cy + dy), List.of())) {
                        boolean bothStore = b.type() != FacilityType.HOSPITAL;
                        // Each storing pair once (by id order); every centre-to-hospital pair once.
                        if (b == a || (bothStore && b.id().compareTo(a.id()) < 0)) {
                            continue;
                        }
                        double straight = a.distanceTo(b);
                        if (straight <= MAX_ROUTE_KM) {
                            routes.add(new RouteEntry(a.id(), b.id(), roadRoute(straight)));
                        }
                    }
                }
            }
        }
        return routes;
    }

    /** Road distance with a detour factor; about 46 km/h plus 10 minutes to load and hand over. */
    static Route roadRoute(double straightKm) {
        int km = (int) Math.round(straightKm * 1.35) + 1;
        return new Route(km, (int) Math.round(km * 1.3) + 10);
    }

    private static ComponentType pick(RandomSource random) {
        double r = random.nextDouble();
        double cumulative = 0;
        for (Map.Entry<ComponentType, Double> e : STOCK_MIX.entrySet()) {
            cumulative += e.getValue();
            if (r < cumulative) {
                return e.getKey();
            }
        }
        return ComponentType.RED_CELLS;
    }

    private static long cell(double x, double y) {
        return key((long) Math.floor(x / MAX_ROUTE_KM), (long) Math.floor(y / MAX_ROUTE_KM));
    }

    private static long key(long cx, long cy) {
        return (cx << 32) ^ (cy & 0xffffffffL);
    }
}
