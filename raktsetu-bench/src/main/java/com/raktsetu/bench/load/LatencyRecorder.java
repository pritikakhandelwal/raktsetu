package com.raktsetu.bench.load;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

/**
 * Collects the response time of every call, per operation, from many threads at once, and summarises them as
 * percentiles. Keeps every sample (a few hundred thousand longs per run), so percentiles are exact.
 */
public final class LatencyRecorder {

    /** Percentiles of one operation, in milliseconds. */
    public record Stats(String operation, long calls, long errors, double p50, double p95, double p99, double max) {
    }

    private final Map<String, ConcurrentLinkedQueue<Long>> samples = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> errors = new ConcurrentHashMap<>();

    public void record(String operation, long nanos, boolean ok) {
        samples.computeIfAbsent(operation, k -> new ConcurrentLinkedQueue<>()).add(nanos);
        if (!ok) {
            errors.computeIfAbsent(operation, k -> new LongAdder()).increment();
        }
    }

    public long totalCalls() {
        return samples.values().stream().mapToLong(ConcurrentLinkedQueue::size).sum();
    }

    public long totalErrors() {
        return errors.values().stream().mapToLong(LongAdder::sum).sum();
    }

    /** Stats per operation, in the order operations were first seen, plus an "all" row. */
    public List<Stats> summary() {
        Map<String, long[]> sorted = new LinkedHashMap<>();
        List<Long> all = new ArrayList<>();
        samples.forEach((op, q) -> {
            long[] values = q.stream().mapToLong(Long::longValue).sorted().toArray();
            sorted.put(op, values);
            q.forEach(all::add);
        });
        List<Stats> out = new ArrayList<>();
        sorted.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.add(stats(e.getKey(), e.getValue(), errorsOf(e.getKey()))));
        out.add(stats("all", all.stream().mapToLong(Long::longValue).sorted().toArray(), totalErrors()));
        return out;
    }

    private long errorsOf(String op) {
        LongAdder e = errors.get(op);
        return e == null ? 0 : e.sum();
    }

    static Stats stats(String op, long[] sortedNanos, long errors) {
        return new Stats(op, sortedNanos.length, errors, percentile(sortedNanos, 50), percentile(sortedNanos, 95),
                percentile(sortedNanos, 99), sortedNanos.length == 0 ? 0 : sortedNanos[sortedNanos.length - 1] / 1e6);
    }

    /** Nearest-rank percentile, in milliseconds. */
    static double percentile(long[] sortedNanos, double p) {
        if (sortedNanos.length == 0) {
            return 0;
        }
        int rank = (int) Math.ceil(p / 100 * sortedNanos.length);
        return sortedNanos[Math.max(0, rank - 1)] / 1e6;
    }

    @Override
    public String toString() {
        return Arrays.toString(summary().toArray());
    }
}
