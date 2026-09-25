package com.raktsetu.domain.model;

/**
 * A road route between two facilities.
 *
 * @param distanceKm    road distance in kilometres
 * @param travelMinutes typical travel time by road
 */
public record Route(int distanceKm, int travelMinutes) {

    public static final Route ZERO = new Route(0, 0);

    public Route {
        if (distanceKm < 0 || travelMinutes < 0) {
            throw new IllegalArgumentException("Distance and travel time cannot be negative");
        }
    }
}
