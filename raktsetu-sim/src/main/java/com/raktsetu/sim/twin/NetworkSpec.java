package com.raktsetu.sim.twin;

import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.NetworkData;
import com.raktsetu.domain.model.RouteEntry;
import com.raktsetu.domain.model.UsageHistory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Everything that describes a simulated network: its facilities, the road routes between them, how much of each
 * component a hospital of weight 1.0 uses per day, and how far total collections exceed total demand.
 *
 * @param name         short name, for example {@code pune-pilot}
 * @param sites        facilities
 * @param routes       road routes (distance and travel time)
 * @param dailyDemand  units per day per unit of hospital demand weight, by component
 * @param supplyFactor total collections divided by total expected demand (1.10 = 10% more collected than used)
 */
public record NetworkSpec(String name, List<Site> sites, List<RouteEntry> routes,
                          Map<ComponentType, Double> dailyDemand, double supplyFactor) {

    public NetworkSpec {
        Objects.requireNonNull(name, "name");
        sites = List.copyOf(sites);
        routes = List.copyOf(routes);
        dailyDemand = Map.copyOf(new EnumMap<>(dailyDemand));
        if (supplyFactor <= 0) {
            throw new IllegalArgumentException("Supply factor must be positive");
        }
        if (sites.stream().noneMatch(Site::isHospital) || sites.stream().allMatch(Site::isHospital)) {
            throw new IllegalArgumentException("A network needs at least one hospital and one blood centre");
        }
    }

    public List<Site> centres() {
        return sites.stream().filter(s -> !s.isHospital()).toList();
    }

    public List<Site> hospitals() {
        return sites.stream().filter(Site::isHospital).toList();
    }

    /** Total expected daily demand for one component across all hospitals. */
    public double totalDailyDemand(ComponentType type) {
        double weight = hospitals().stream().mapToDouble(Site::demandWeight).sum();
        return weight * dailyDemand.getOrDefault(type, 0.0);
    }

    /** A fresh network with no stock and no history, every facility reported on {@code start}. */
    public NetworkData emptyNetwork(LocalDate start) {
        List<Facility> facilities = new ArrayList<>();
        for (Site s : sites) {
            facilities.add(s.type().newFacility(s.id(), s.name(), start));
        }
        return new NetworkData(facilities, routes, new UsageHistory(), List.of(), 1);
    }
}
