package com.raktsetu.server.alerts;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A stand-in for an SMS or WhatsApp provider's API on a local port: records every request and answers with the
 * statuses it is told to (201 when none are queued).
 */
public final class FakeProvider implements AutoCloseable {

    public record Received(String method, String path, String authorization, String contentType, String body) {
    }

    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final Deque<Integer> statuses = new ArrayDeque<>();

    public FakeProvider() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Content-Type"), body));
            int status;
            synchronized (statuses) {
                status = statuses.isEmpty() ? 201 : statuses.poll();
            }
            byte[] reply = ("{\"status\":" + status + "}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** The next answers, in order. */
    public void answer(Integer... next) {
        synchronized (statuses) {
            statuses.clear();
            statuses.addAll(List.of(next));
        }
    }

    public List<Received> received() {
        return received;
    }

    public void reset() {
        received.clear();
        answer();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
