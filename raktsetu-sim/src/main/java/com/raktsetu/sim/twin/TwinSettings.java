package com.raktsetu.sim.twin;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Settings for one simulation run.
 *
 * @param start       first simulated day
 * @param warmupDays  days simulated before measurement starts, so stock and usage history reach a steady state
 * @param days        days measured after the warm-up
 * @param compliance  probability that a centre reports its stock on a given day (1.0 = every day)
 * @param seed        base seed for all randomness
 * @param replication replication number; each replication gets different, but reproducible, randomness
 * @param demand      how demand varies by weekday and season ({@link DemandPattern#FLAT} for none)
 */
public record TwinSettings(LocalDate start, int warmupDays, int days, double compliance, long seed, int replication,
                           DemandPattern demand) {

    /** With flat demand. */
    public TwinSettings(LocalDate start, int warmupDays, int days, double compliance, long seed, int replication) {
        this(start, warmupDays, days, compliance, seed, replication, DemandPattern.FLAT);
    }

    public TwinSettings {
        Objects.requireNonNull(demand, "demand");
        Objects.requireNonNull(start, "start");
        if (warmupDays < 0 || days < 1) {
            throw new IllegalArgumentException("Need a non-negative warm-up and at least one measured day");
        }
        if (compliance < 0 || compliance > 1) {
            throw new IllegalArgumentException("Compliance must be between 0 and 1");
        }
    }

    public int totalDays() {
        return warmupDays + days;
    }
}
