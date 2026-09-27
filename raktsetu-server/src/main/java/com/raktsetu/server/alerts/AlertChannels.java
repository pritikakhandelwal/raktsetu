package com.raktsetu.server.alerts;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * All channels Spring found, and which are in use: every configured provider (SMS, WhatsApp), or the log when none
 * is configured. Adding a provider means adding one {@link AlertChannel} class; nothing here changes.
 */
@Component
public class AlertChannels {

    /** A channel as administrators see it. */
    public record Status(String name, boolean configured, boolean active) {
    }

    private final List<AlertChannel> all;

    public AlertChannels(List<AlertChannel> all) {
        this.all = List.copyOf(all);
    }

    public List<AlertChannel> active() {
        List<AlertChannel> providers = all.stream()
                .filter(c -> !(c instanceof LogChannel) && c.configured()).toList();
        return providers.isEmpty() ? all.stream().filter(c -> c instanceof LogChannel).toList() : providers;
    }

    public Optional<AlertChannel> byName(String name) {
        return all.stream().filter(c -> c.name().equals(name)).findFirst();
    }

    public List<Status> status() {
        List<AlertChannel> active = active();
        return all.stream().map(c -> new Status(c.name(), c.configured(), active.contains(c))).toList();
    }
}
