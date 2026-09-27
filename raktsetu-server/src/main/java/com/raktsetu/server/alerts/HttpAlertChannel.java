package com.raktsetu.server.alerts;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Shared plumbing for channels that call a provider's HTTP API (Template Method): subclasses build the request;
 * this class sends it with timeouts and turns failures into {@link AlertDeliveryException}s that say whether a
 * retry could help.
 */
public abstract class HttpAlertChannel implements AlertChannel {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient http;

    protected HttpAlertChannel(HttpClient http) {
        this.http = http;
    }

    protected HttpAlertChannel() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    /** The provider's request for one message. */
    protected abstract HttpRequest.Builder request(String phone, String message);

    @Override
    public final void send(String phone, String message) throws AlertDeliveryException {
        if (!configured()) {
            throw new AlertDeliveryException(name() + " is not configured", false);
        }
        HttpResponse<String> response;
        try {
            response = http.send(request(phone, message).timeout(TIMEOUT).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AlertDeliveryException(name() + " could not be reached: " + e.getMessage(), true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AlertDeliveryException(name() + " send was interrupted", true, e);
        }
        if (response.statusCode() / 100 != 2) {
            throw AlertDeliveryException.fromStatus(name(), response.statusCode(), response.body());
        }
    }

    protected static URI uri(String base, String path) {
        return URI.create(base.replaceAll("/+$", "") + path);
    }

    protected static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
