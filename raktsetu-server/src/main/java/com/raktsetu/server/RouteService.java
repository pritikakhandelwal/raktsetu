package com.raktsetu.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Works out road routes between facilities. It asks an OSRM routing service (OpenStreetMap data) for real driving
 * distances and times; if that is switched off or unavailable, it falls back to a straight-line estimate. OSRM gives
 * free-flow times, so they are scaled for Indian traffic and a fixed handling time is added.
 */
@Component
public class RouteService {

    /** Real transport is slower than free-flow driving. */
    static final double TRAFFIC_FACTOR = 1.5;
    /** Packing, loading and hand-over. */
    static final int HANDLING_MINUTES = 10;
    /** Roads are longer than the straight line. */
    static final double ROAD_FACTOR = 1.35;
    /** Average speed for estimates, km/h. */
    static final double ESTIMATE_SPEED_KMH = 30;

    private static final Logger LOG = LoggerFactory.getLogger(RouteService.class);

    /** A facility's location. */
    public record Place(String id, double latitude, double longitude) {
    }

    /** One direction of travel. */
    public record Leg(String fromId, String toId, int distanceKm, int travelMinutes, String source) {
    }

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public RouteService(RaktSetuProperties properties) {
        this.baseUrl = properties.routingUrl();
    }

    /** Routes in both directions between {@code place} and each of {@code others}. Never fails: falls back to estimates. */
    public List<Leg> between(Place place, List<Place> others) {
        if (others.isEmpty()) {
            return List.of();
        }
        if (!baseUrl.isBlank()) {
            try {
                return fromOsrm(place, others);
            } catch (Exception e) {
                LOG.warn("Road routing unavailable ({}); using straight-line estimates", e.getMessage());
            }
        }
        List<Leg> legs = new ArrayList<>();
        for (Place other : others) {
            legs.add(estimate(place, other));
            legs.add(estimate(other, place));
        }
        return legs;
    }

    private List<Leg> fromOsrm(Place place, List<Place> others) throws Exception {
        List<Place> all = new ArrayList<>();
        all.add(place);
        all.addAll(others);
        String coords = all.stream().map(p -> String.format(Locale.ROOT, "%.6f,%.6f", p.longitude(), p.latitude()))
                .collect(Collectors.joining(";"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/table/v1/driving/" + coords
                        + "?annotations=duration,distance"))
                .timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("routing service answered " + response.statusCode());
        }
        JsonNode table = json.readTree(response.body());
        if (!"Ok".equals(table.path("code").asString())) {
            throw new IllegalStateException("routing service error " + table.path("code").asString());
        }
        List<Leg> legs = new ArrayList<>();
        for (int j = 1; j < all.size(); j++) {
            legs.add(leg(table, 0, j, all));
            legs.add(leg(table, j, 0, all));
        }
        return legs;
    }

    private static Leg leg(JsonNode table, int i, int j, List<Place> all) {
        JsonNode seconds = table.path("durations").path(i).path(j);
        JsonNode metres = table.path("distances").path(i).path(j);
        if (seconds.isNull() || metres.isNull() || seconds.isMissingNode() || metres.isMissingNode()) {
            return estimate(all.get(i), all.get(j)); // no road between them
        }
        int minutes = (int) Math.round(seconds.asDouble() / 60 * TRAFFIC_FACTOR) + HANDLING_MINUTES;
        return new Leg(all.get(i).id(), all.get(j).id(), (int) Math.round(metres.asDouble() / 1000), minutes, "OSRM");
    }

    /** Straight-line distance times a road factor, at an average speed, plus handling time. */
    static Leg estimate(Place a, Place b) {
        double km = haversineKm(a, b) * ROAD_FACTOR;
        int minutes = (int) Math.round(km / ESTIMATE_SPEED_KMH * 60) + HANDLING_MINUTES;
        return new Leg(a.id(), b.id(), (int) Math.max(1, Math.round(km)), minutes, "ESTIMATE");
    }

    static double haversineKm(Place a, Place b) {
        double r = 6371.0;
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(a.latitude()))
                * Math.cos(Math.toRadians(b.latitude())) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * r * Math.asin(Math.sqrt(h));
    }
}
