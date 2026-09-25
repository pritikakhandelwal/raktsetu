package com.raktsetu.domain.model;

import com.raktsetu.domain.interfaces.Identifiable;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * A place in the network: a blood centre that collects, a storage centre, or a hospital that raises requests.
 *
 * <p>Each facility creates and owns its {@link Inventory} (composition): the inventory cannot exist without it. The
 * {@code lastReportedOn} date records when the facility last reported its stock, which drives the data-freshness
 * features.
 */
public abstract class Facility implements Identifiable {

    private final String id;
    private final String name;
    private final Inventory inventory = new Inventory();
    private LocalDate lastReportedOn;

    protected Facility(String id, String name) {
        this(id, name, null);
    }

    protected Facility(String id, String name, LocalDate lastReportedOn) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Facility ID is required");
        }
        this.id = id.trim();
        this.name = Objects.requireNonNull(name, "name").trim();
        this.lastReportedOn = lastReportedOn;
    }

    /** Whether donors can give blood here. */
    public abstract boolean canCollect();

    /** Whether this facility holds stock that can be issued or transferred. */
    public abstract boolean canStore();

    public abstract FacilityType getFacilityType();

    @Override
    public final String getId() {
        return id;
    }

    public final String getName() {
        return name;
    }

    /**
     * The stock held here.
     *
     * @throws IllegalStateException if this facility does not store blood
     */
    public final Inventory getInventory() {
        if (!canStore()) {
            throw new IllegalStateException(name + " (" + id + ") does not store blood");
        }
        return inventory;
    }

    /** When stock was last reported, or {@code null} if never. */
    public final LocalDate getLastReportedOn() {
        return lastReportedOn;
    }

    public final void markReported(LocalDate date) {
        this.lastReportedOn = Objects.requireNonNull(date, "date");
    }

    /** Days since the last stock report, or {@link Long#MAX_VALUE} if the facility has never reported. */
    public final long daysSinceReport(LocalDate today) {
        return lastReportedOn == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(lastReportedOn, today);
    }

    @Override
    public final boolean equals(Object o) {
        return o instanceof Facility other && id.equals(other.id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }
}
