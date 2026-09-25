package com.raktsetu.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Everything needed to rebuild the network: facilities with their stock, the distance table, usage history, wastage
 * so far, and the next serial number for new unit IDs.
 */
public record NetworkData(List<Facility> facilities, List<RouteEntry> routes, UsageHistory usage,
                          List<WastageRecord> wastage, long nextUnitSerial) {

    public NetworkData {
        facilities = List.copyOf(facilities);
        routes = List.copyOf(routes);
        Objects.requireNonNull(usage, "usage");
        wastage = List.copyOf(wastage);
        if (nextUnitSerial < 1) {
            throw new IllegalArgumentException("Unit serial numbers start at 1");
        }
    }
}
