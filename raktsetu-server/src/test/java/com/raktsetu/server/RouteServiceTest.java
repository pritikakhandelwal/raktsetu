package com.raktsetu.server;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteServiceTest {

    private static final RouteService.Place PUNE = new RouteService.Place("A", 18.5204, 73.8567);
    private static final RouteService.Place BARAMATI = new RouteService.Place("B", 18.1515, 74.5815);

    private static RouteService service(String url) {
        return new RouteService(new RaktSetuProperties(Path.of("x"), null, url, null));
    }

    @Test
    void offlineEstimatesUseStraightLineTimesARoadFactor() {
        List<RouteService.Leg> legs = service("").between(PUNE, List.of(BARAMATI));

        assertThat(legs).hasSize(2);
        RouteService.Leg there = legs.get(0);
        assertThat(there.source()).isEqualTo("ESTIMATE");
        // about 88 km in a straight line, so about 119 km by road at 30 km/h plus 10 minutes' handling
        assertThat(there.distanceKm()).isBetween(110, 125);
        assertThat(there.travelMinutes()).isBetween(230, 260);
        assertThat(service("").between(PUNE, List.of())).isEmpty();
    }

    @Test
    void roadRoutesComeFromTheRoutingService() throws Exception {
        HttpServer osrm = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        osrm.createContext("/table/v1/driving/", exchange -> {
            byte[] body = ("{\"code\":\"Ok\",\"durations\":[[0,4620],[4560,0]],"
                    + "\"distances\":[[0,104000],[103500,0]]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        osrm.start();
        try {
            String url = "http://127.0.0.1:" + osrm.getAddress().getPort();
            List<RouteService.Leg> legs = service(url).between(PUNE, List.of(BARAMATI));

            assertThat(legs).containsExactly(
                    new RouteService.Leg("A", "B", 104, 126, "OSRM"),   // 77 min x 1.5 + 10
                    new RouteService.Leg("B", "A", 104, 124, "OSRM"));  // 76 min x 1.5 + 10
        } finally {
            osrm.stop(0);
        }
    }

    @Test
    void anUnreachableRoutingServiceFallsBackToEstimates() {
        List<RouteService.Leg> legs = service("http://127.0.0.1:1").between(PUNE, List.of(BARAMATI));
        assertThat(legs).extracting(RouteService.Leg::source).containsOnly("ESTIMATE");
    }
}
