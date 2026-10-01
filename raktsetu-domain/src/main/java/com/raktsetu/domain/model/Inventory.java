package com.raktsetu.domain.model;

import com.raktsetu.domain.exception.InsufficientStockException;
import com.raktsetu.domain.exception.UnitExpiredException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The stock held by one facility, kept in first-expiry-first-out order per component and blood group.
 *
 * <p>An inventory is created and owned by its {@link Facility} (composition) and is not shared. It never issues an
 * expired unit: callers must call {@link #removeExpired(LocalDate)} each day, which returns the discarded units so they
 * can be recorded as wastage.
 *
 * <p><b>Data structure.</b> Each component and group is a {@link TreeSet} (a red-black tree) ordered by
 * {@link BloodComponent#FEFO}. Adding, issuing the soonest-expiring unit and removing any one unit all take
 * O(log n), and the units can be read in expiry order without copying or sorting. Expired units are always at the
 * front, so counting usable units only has to step past them. A binary heap ({@code PriorityQueue}) would add and
 * issue as fast, but reading it in order means copying and sorting, and removing a given unit is O(n).
 */
public final class Inventory {

    private final Map<ComponentType, Map<BloodGroup, TreeSet<BloodComponent>>> stock =
            new EnumMap<>(ComponentType.class);
    private final Map<String, BloodComponent> byId = new HashMap<>();
    /**
     * No unit held expires before this date (a lower bound, or {@code null} when empty). Adding a unit can only lower
     * it and removing one never invalidates it, so {@link #removeExpired} can return at once when nothing is due.
     */
    private LocalDate earliestExpiry;

    Inventory() {
    }

    /**
     * Adds a unit.
     *
     * @throws IllegalArgumentException if a unit with the same ID is already held
     */
    public void add(BloodComponent unit) {
        if (byId.putIfAbsent(unit.getId(), unit) != null) {
            throw new IllegalArgumentException("Unit " + unit.getId() + " is already in stock");
        }
        set(unit.getType(), unit.getBloodGroup()).add(unit);
        if (earliestExpiry == null || unit.getExpiryDate().isBefore(earliestExpiry)) {
            earliestExpiry = unit.getExpiryDate();
        }
    }

    /** Total units held of this component and group, including any not yet removed as expired. */
    public int count(ComponentType type, BloodGroup group) {
        TreeSet<BloodComponent> s = existingSet(type, group);
        return s == null ? 0 : s.size();
    }

    /** Units of this component and group that can still be issued on {@code today}. */
    public int countUsable(ComponentType type, BloodGroup group, LocalDate today) {
        TreeSet<BloodComponent> s = existingSet(type, group);
        if (s == null) {
            return 0;
        }
        int expired = 0;
        for (Iterator<BloodComponent> it = s.iterator(); it.hasNext() && it.next().isExpired(today); ) {
            expired++;
        }
        return s.size() - expired;
    }

    /**
     * The units of this component and group still usable on {@code today}, soonest-expiring first, as a read-only
     * live view: nothing is copied, so a caller that only needs the first few units only pays for those.
     */
    public SortedSet<BloodComponent> usable(ComponentType type, BloodGroup group, LocalDate today) {
        TreeSet<BloodComponent> s = existingSet(type, group);
        if (s == null) {
            return Collections.emptySortedSet();
        }
        for (BloodComponent u : s) {
            if (!u.isExpired(today)) {
                return Collections.unmodifiableSortedSet(s.tailSet(u, true));
            }
        }
        return Collections.emptySortedSet();
    }

    public int totalUnits() {
        return byId.size();
    }

    /** A snapshot of the units of this component and group, soonest-expiring first. */
    public List<BloodComponent> units(ComponentType type, BloodGroup group) {
        TreeSet<BloodComponent> s = existingSet(type, group);
        return s == null ? List.of() : List.copyOf(s);
    }

    /** A snapshot of every unit, soonest-expiring first. */
    public List<BloodComponent> allUnits() {
        List<BloodComponent> list = new ArrayList<>(byId.values());
        list.sort(BloodComponent.FEFO);
        return List.copyOf(list);
    }

    public Optional<BloodComponent> find(String unitId) {
        return Optional.ofNullable(byId.get(unitId));
    }

    /**
     * Issues {@code quantity} units, soonest-expiring first. Either all requested units are issued or none are.
     *
     * @throws InsufficientStockException if fewer usable units are held than requested
     * @throws UnitExpiredException       if an expired unit is still in stock; call {@link #removeExpired} first
     */
    public List<BloodComponent> issue(ComponentType type, BloodGroup group, int quantity, LocalDate today)
            throws InsufficientStockException, UnitExpiredException {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive, was " + quantity);
        }
        TreeSet<BloodComponent> s = existingSet(type, group);
        BloodComponent head = s == null || s.isEmpty() ? null : s.first();
        if (head != null && head.isExpired(today)) {
            // FEFO order means any expired unit is at the front.
            throw new UnitExpiredException(head.getId(), head.getExpiryDate());
        }
        int available = s == null ? 0 : s.size();
        if (available < quantity) {
            throw new InsufficientStockException(group.label() + " " + type, quantity, available);
        }
        List<BloodComponent> issued = new ArrayList<>(quantity);
        for (int i = 0; i < quantity; i++) {
            BloodComponent unit = s.pollFirst();
            byId.remove(unit.getId());
            issued.add(unit);
        }
        return issued;
    }

    /** Removes one specific unit, for example when it is transferred out. */
    public Optional<BloodComponent> take(String unitId) {
        BloodComponent unit = byId.remove(unitId);
        if (unit != null) {
            set(unit.getType(), unit.getBloodGroup()).remove(unit);
        }
        return Optional.ofNullable(unit);
    }

    /** Removes every unit that is expired on {@code today} and returns them, soonest-expired first. */
    public List<BloodComponent> removeExpired(LocalDate today) {
        if (earliestExpiry == null || today.isBefore(earliestExpiry)) {
            return List.of(); // nothing held can have expired yet
        }
        List<BloodComponent> removed = new ArrayList<>();
        LocalDate earliest = null;
        for (Map<BloodGroup, TreeSet<BloodComponent>> byGroup : stock.values()) {
            for (NavigableSet<BloodComponent> s : byGroup.values()) {
                while (!s.isEmpty() && s.first().isExpired(today)) {
                    BloodComponent unit = s.pollFirst();
                    byId.remove(unit.getId());
                    removed.add(unit);
                }
                if (!s.isEmpty() && (earliest == null || s.first().getExpiryDate().isBefore(earliest))) {
                    earliest = s.first().getExpiryDate();
                }
            }
        }
        earliestExpiry = earliest;
        removed.sort(BloodComponent.FEFO);
        return removed;
    }

    /** Units that expire within {@code days} days of {@code today} but are not yet expired. */
    public List<BloodComponent> expiringWithin(int days, LocalDate today) {
        List<BloodComponent> result = new ArrayList<>();
        for (BloodComponent u : allUnits()) {
            long left = u.daysToExpiry(today);
            if (left > 0 && left <= days) {
                result.add(u);
            }
        }
        return result;
    }

    private TreeSet<BloodComponent> set(ComponentType type, BloodGroup group) {
        return stock.computeIfAbsent(type, t -> new EnumMap<>(BloodGroup.class))
                .computeIfAbsent(group, g -> new TreeSet<>(BloodComponent.FEFO));
    }

    private TreeSet<BloodComponent> existingSet(ComponentType type, BloodGroup group) {
        Map<BloodGroup, TreeSet<BloodComponent>> byGroup = stock.get(type);
        return byGroup == null ? null : byGroup.get(group);
    }
}
