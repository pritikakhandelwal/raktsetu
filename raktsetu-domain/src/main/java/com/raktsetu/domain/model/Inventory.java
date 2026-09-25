package com.raktsetu.domain.model;

import com.raktsetu.domain.exception.InsufficientStockException;
import com.raktsetu.domain.exception.UnitExpiredException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * The stock held by one facility, kept in first-expiry-first-out order per component and blood group.
 *
 * <p>An inventory is created and owned by its {@link Facility} (composition) and is not shared. It never issues an
 * expired unit: callers must call {@link #removeExpired(LocalDate)} each day, which returns the discarded units so they
 * can be recorded as wastage.
 */
public final class Inventory {

    private final Map<ComponentType, Map<BloodGroup, PriorityQueue<BloodComponent>>> stock =
            new EnumMap<>(ComponentType.class);
    private final Map<String, BloodComponent> byId = new HashMap<>();

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
        queue(unit.getType(), unit.getBloodGroup()).add(unit);
    }

    /** Total units held of this component and group, including any not yet removed as expired. */
    public int count(ComponentType type, BloodGroup group) {
        PriorityQueue<BloodComponent> q = existingQueue(type, group);
        return q == null ? 0 : q.size();
    }

    /** Units of this component and group that can still be issued on {@code today}. */
    public int countUsable(ComponentType type, BloodGroup group, LocalDate today) {
        PriorityQueue<BloodComponent> q = existingQueue(type, group);
        if (q == null) {
            return 0;
        }
        int n = 0;
        for (BloodComponent u : q) {
            if (!u.isExpired(today)) {
                n++;
            }
        }
        return n;
    }

    public int totalUnits() {
        return byId.size();
    }

    /** A snapshot of the units of this component and group, soonest-expiring first. */
    public List<BloodComponent> units(ComponentType type, BloodGroup group) {
        PriorityQueue<BloodComponent> q = existingQueue(type, group);
        if (q == null) {
            return List.of();
        }
        List<BloodComponent> list = new ArrayList<>(q);
        list.sort(BloodComponent.FEFO);
        return List.copyOf(list);
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
        PriorityQueue<BloodComponent> q = existingQueue(type, group);
        BloodComponent head = q == null ? null : q.peek();
        if (head != null && head.isExpired(today)) {
            // FEFO order means any expired unit is at the head of the queue.
            throw new UnitExpiredException(head.getId(), head.getExpiryDate());
        }
        int available = q == null ? 0 : q.size();
        if (available < quantity) {
            throw new InsufficientStockException(group.label() + " " + type, quantity, available);
        }
        List<BloodComponent> issued = new ArrayList<>(quantity);
        for (int i = 0; i < quantity; i++) {
            BloodComponent unit = q.poll();
            byId.remove(unit.getId());
            issued.add(unit);
        }
        return issued;
    }

    /** Removes one specific unit, for example when it is transferred out. */
    public Optional<BloodComponent> take(String unitId) {
        BloodComponent unit = byId.remove(unitId);
        if (unit != null) {
            queue(unit.getType(), unit.getBloodGroup()).remove(unit);
        }
        return Optional.ofNullable(unit);
    }

    /** Removes every unit that is expired on {@code today} and returns them, soonest-expired first. */
    public List<BloodComponent> removeExpired(LocalDate today) {
        List<BloodComponent> removed = new ArrayList<>();
        for (Map<BloodGroup, PriorityQueue<BloodComponent>> byGroup : stock.values()) {
            for (PriorityQueue<BloodComponent> q : byGroup.values()) {
                while (!q.isEmpty() && q.peek().isExpired(today)) {
                    BloodComponent unit = q.poll();
                    byId.remove(unit.getId());
                    removed.add(unit);
                }
            }
        }
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

    private PriorityQueue<BloodComponent> queue(ComponentType type, BloodGroup group) {
        return stock.computeIfAbsent(type, t -> new EnumMap<>(BloodGroup.class))
                .computeIfAbsent(group, g -> new PriorityQueue<>(BloodComponent.FEFO));
    }

    private PriorityQueue<BloodComponent> existingQueue(ComponentType type, BloodGroup group) {
        Map<BloodGroup, PriorityQueue<BloodComponent>> byGroup = stock.get(type);
        return byGroup == null ? null : byGroup.get(group);
    }
}
