package com.raktsetu.bench.load;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * One signed-in browser session against the RaktSetu API: keeps its own cookies (the session and the CSRF token)
 * and sends the {@code X-XSRF-TOKEN} header on changes, exactly as the web front end does.
 */
public final class ApiClient {

    /** A response: HTTP status and the parsed JSON body (empty object if there was none). */
    public record Response(int status, JsonNode body) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }

        public Response orThrow(String what) {
            if (!ok()) {
                throw new IllegalStateException(what + " failed: HTTP " + status + " " + body);
            }
            return this;
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI base;
    private final CookieManager cookies = new CookieManager();
    private final HttpClient http;

    public ApiClient(URI base) {
        this.base = base;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(10)).build();
    }

    public Response get(String path) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(base.resolve(path)).GET());
    }

    public Response post(String path, Object body) throws IOException, InterruptedException {
        if (csrfToken() == null) {
            get("/api/auth/me"); // the server issues the XSRF-TOKEN cookie on the first request
        }
        String json = body == null ? "" : JSON.writeValueAsString(body);
        return send(HttpRequest.newBuilder(base.resolve(path))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken() == null ? "" : csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    public Response signIn(String username, String password) throws IOException, InterruptedException {
        return post("/api/auth/login", Map.of("username", username, "password", password));
    }

    private Response send(HttpRequest.Builder request) throws IOException, InterruptedException {
        HttpResponse<String> r = http.send(request.timeout(Duration.ofSeconds(30)).build(),
                HttpResponse.BodyHandlers.ofString());
        String text = r.body();
        JsonNode body = text == null || text.isBlank() ? JSON.createObjectNode() : JSON.readTree(text);
        return new Response(r.statusCode(), body);
    }

    private String csrfToken() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("XSRF-TOKEN"))
                .map(HttpCookie::getValue)
                .findFirst().orElse(null);
    }
}
