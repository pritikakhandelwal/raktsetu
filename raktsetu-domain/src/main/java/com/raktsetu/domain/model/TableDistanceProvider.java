package com.raktsetu.domain.model;

import com.raktsetu.domain.interfaces.DistanceProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Distances from a precomputed table. A route given in one direction is used for both directions unless the reverse
 * direction is also given.
 */
public final class TableDistanceProvider implements DistanceProvider {

    private final Map<String, Route> routes = new HashMap<>();
    private final List<RouteEntry> entries;

    public TableDistanceProvider(List<RouteEntry> entries) {
        this.entries = List.copyOf(entries);
        for (RouteEntry e : entries) {
            routes.put(key(e.fromId(), e.toId()), e.route());
        }
        for (RouteEntry e : entries) {
            routes.putIfAbsent(key(e.toId(), e.fromId()), e.route());
        }
    }

    @Override
    public Optional<Route> route(String fromFacilityId, String toFacilityId) {
        if (fromFacilityId.equals(toFacilityId)) {
            return Optional.of(Route.ZERO);
        }
        return Optional.ofNullable(routes.get(key(fromFacilityId, toFacilityId)));
    }

    /** The rows this table was built from, for saving. */
    public List<RouteEntry> entries() {
        return new ArrayList<>(entries);
    }

    private static String key(String from, String to) {
        return from + "\u0000" + to;
    }
}
