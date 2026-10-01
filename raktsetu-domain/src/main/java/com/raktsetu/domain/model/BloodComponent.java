package com.raktsetu.domain.model;

import com.raktsetu.domain.interfaces.ExpiryTracked;
import com.raktsetu.domain.interfaces.Identifiable;
import com.raktsetu.domain.interfaces.Transfusable;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;

/**
 * One physical unit (bag) of a blood component.
 *
 * <p>Subclasses supply the shelf life, storage condition and compatibility rule, which is where the polymorphism
 * of the design lives. Units are immutable; moving or issuing a unit is recorded elsewhere.
 */
public abstract class BloodComponent implements Identifiable, ExpiryTracked, Transfusable {

    /** First-expiry-first-out order; ties broken by ID so the order is always deterministic. */
    public static final Comparator<BloodComponent> FEFO = (a, b) -> {
        int byExpiry = a.getExpiryDate().compareTo(b.getExpiryDate());
        return byExpiry != 0 ? byExpiry : a.unitId.compareTo(b.unitId);
    };

    private final String unitId;
    private final BloodGroup bloodGroup;
    private final LocalDate collectedOn;
    private final LocalDate expiryOverride;
    /**
     * Expiry, worked out on first use and then remembered: sorting stock by expiry asks for it many times. Safe to
     * share between threads without locking, because {@link LocalDate} is immutable and every thread computes the
     * same value.
     */
    private LocalDate expiry;

    /** A unit whose expiry follows the component's standard shelf life. */
    protected BloodComponent(String unitId, BloodGroup bloodGroup, LocalDate collectedOn) {
        this(unitId, bloodGroup, collectedOn, null);
    }

    /**
     * A unit with an explicit expiry date, for example when processing (such as irradiation) shortens its life.
     *
     * @param expiryOverride explicit expiry, or {@code null} to use the standard shelf life
     */
    protected BloodComponent(String unitId, BloodGroup bloodGroup, LocalDate collectedOn, LocalDate expiryOverride) {
        if (unitId == null || unitId.isBlank()) {
            throw new IllegalArgumentException("Unit ID is required");
        }
        this.unitId = unitId.trim();
        this.bloodGroup = Objects.requireNonNull(bloodGroup, "bloodGroup");
        this.collectedOn = Objects.requireNonNull(collectedOn, "collectedOn");
        if (expiryOverride != null && !expiryOverride.isAfter(collectedOn)) {
            throw new IllegalArgumentException("Expiry must be after the collection date for unit " + unitId);
        }
        this.expiryOverride = expiryOverride;
    }

    public abstract ComponentType getType();

    /** Standard shelf life in days from collection. Confirm values against current NACO/NBTC standards. */
    public abstract int getShelfLifeDays();

    public abstract String getStorageCondition();

    @Override
    public final String getId() {
        return unitId;
    }

    public final BloodGroup getBloodGroup() {
        return bloodGroup;
    }

    public final LocalDate getCollectedOn() {
        return collectedOn;
    }

    @Override
    public final LocalDate getExpiryDate() {
        LocalDate e = expiry;
        if (e == null) {
            e = expiryOverride != null ? expiryOverride : standardExpiryDate();
            expiry = e;
        }
        return e;
    }

    /** Expiry from the standard shelf life, ignoring any explicit override. */
    public final LocalDate standardExpiryDate() {
        return collectedOn.plusDays(getShelfLifeDays());
    }

    /** Units are the same unit if and only if they have the same ID. */
    @Override
    public final boolean equals(Object o) {
        return o instanceof BloodComponent other && unitId.equals(other.unitId);
    }

    @Override
    public final int hashCode() {
        return unitId.hashCode();
    }

    @Override
    public String toString() {
        return getType() + " " + bloodGroup.label() + " [" + unitId + ", expires " + getExpiryDate() + "]";
    }
}
