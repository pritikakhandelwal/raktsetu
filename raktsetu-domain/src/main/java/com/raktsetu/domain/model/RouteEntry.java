package com.raktsetu.domain.model;

import java.util.Objects;

/** One row of the distance table: a route from one facility to another. */
public record RouteEntry(String fromId, String toId, Route route) {

    public RouteEntry {
        Objects.requireNonNull(fromId, "fromId");
        Objects.requireNonNull(toId, "toId");
        Objects.requireNonNull(route, "route");
    }
}
