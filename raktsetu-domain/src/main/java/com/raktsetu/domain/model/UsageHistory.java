package com.raktsetu.domain.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * How many units each facility issued per day, by component and blood group. The expiry-risk check uses the recent
 * average to judge whether stock will be used before it expires.
 */
public final class UsageHistory {

    /** Length of the moving-average window used by default. */
    public static final int DEFAULT_WINDOW_DAYS = 14;

    /** One day's issues for one facility, component and group. */
    public record Entry(LocalDate date, String facilityId, ComponentType component, BloodGroup group, int units) {
        public Entry {
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(facilityId, "facilityId");
            Objects.requireNonNull(component, "component");
            Objects.requireNonNull(group, "group");
            if (units < 0) {
                throw new IllegalArgumentException("Units issued cannot be negative");
            }
        }
    }

    private record Key(String facilityId, ComponentType component, BloodGroup group) {
    }

    private final Map<Key, NavigableMap<LocalDate, Integer>> daily = new HashMap<>();

    /** Adds {@code units} issued on {@code date}; repeated calls for the same day accumulate. */
    public void record(LocalDate date, String facilityId, ComponentType component, BloodGroup group, int units) {
        Entry e = new Entry(date, facilityId, component, group, units);
        if (units == 0) {
            return;
        }
        daily.computeIfAbsent(new Key(e.facilityId(), component, group), k -> new TreeMap<>())
                .merge(date, units, Integer::sum);
    }

    /**
     * Average units issued per day over the {@code windowDays} days before {@code today}. Today is excluded because it
     * is not complete yet.
     */
    public double averageDaily(String facilityId, ComponentType component, BloodGroup group, LocalDate today,
                               int windowDays) {
        if (windowDays < 1) {
            throw new IllegalArgumentException("Window must be at least one day");
        }
        NavigableMap<LocalDate, Integer> days = daily.get(new Key(facilityId, component, group));
        if (days == null) {
            return 0.0;
        }
        int total = 0;
        for (int units : days.subMap(today.minusDays(windowDays), true, today, false).values()) {
            total += units;
        }
        return (double) total / windowDays;
    }

    public double averageDaily(String facilityId, ComponentType component, BloodGroup group, LocalDate today) {
        return averageDaily(facilityId, component, group, today, DEFAULT_WINDOW_DAYS);
    }

    /**
     * One facility's recorded days in {@code [from, toExclusive)}, sorted by date, component and group. Used to pass on
     * only what a facility has reported so far.
     */
    public List<Entry> entriesFor(String facilityId, LocalDate from, LocalDate toExclusive) {
        List<Entry> list = new ArrayList<>();
        daily.forEach((k, days) -> {
            if (k.facilityId().equals(facilityId)) {
                days.subMap(from, true, toExclusive, false).forEach(
                        (date, units) -> list.add(new Entry(date, k.facilityId(), k.component(), k.group(), units)));
            }
        });
        list.sort(Comparator.comparing(Entry::date).thenComparing(Entry::component).thenComparing(Entry::group));
        return list;
    }

    /** Every recorded day, sorted by date, facility, component and group, for saving. */
    public List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        daily.forEach((k, days) -> days.forEach(
                (date, units) -> list.add(new Entry(date, k.facilityId(), k.component(), k.group(), units))));
        list.sort(Comparator.comparing(Entry::date).thenComparing(Entry::facilityId)
                .thenComparing(Entry::component).thenComparing(Entry::group));
        return list;
    }
}
