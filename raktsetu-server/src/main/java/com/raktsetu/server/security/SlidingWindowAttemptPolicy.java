package com.raktsetu.server.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Locks a key after {@code maxFailures} failed sign-ins within {@code window}. The lock lasts until the oldest of
 * those failures leaves the window, so it clears by itself; there is nothing to unlock by hand.
 *
 * <p>Failures are kept in memory, one queue of timestamps per key, and old timestamps are dropped as they expire.
 * A restart forgets them, which is acceptable for a throttle whose job is to slow down password guessing.
 */
public final class SlidingWindowAttemptPolicy implements LoginAttemptPolicy {

    private final int maxFailures;
    private final Duration window;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public SlidingWindowAttemptPolicy(int maxFailures, Duration window) {
        if (maxFailures < 1) {
            throw new IllegalArgumentException("maxFailures must be at least 1");
        }
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.maxFailures = maxFailures;
        this.window = window;
    }

    @Override
    public Optional<Duration> lockedFor(String key, Instant now) {
        Deque<Instant> times = failures.get(key);
        if (times == null) {
            return Optional.empty();
        }
        synchronized (times) {
            dropExpired(times, now);
            if (times.size() < maxFailures) {
                return Optional.empty();
            }
            // Locked until enough failures have aged out to fall below the limit.
            Instant oldestCounted = times.stream().skip(times.size() - maxFailures).findFirst().orElseThrow();
            return Optional.of(Duration.between(now, oldestCounted.plus(window)));
        }
    }

    @Override
    public void recordFailure(String key, Instant now) {
        Deque<Instant> times = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            dropExpired(times, now);
            times.addLast(now);
        }
    }

    @Override
    public void reset(String key) {
        failures.remove(key);
    }

    /** Number of keys with recent failures; for monitoring. */
    public int trackedKeys() {
        return failures.size();
    }

    private void dropExpired(Deque<Instant> times, Instant now) {
        Instant cutoff = now.minus(window);
        while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
            times.removeFirst();
        }
    }
}
