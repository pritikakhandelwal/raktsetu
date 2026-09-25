package com.raktsetu.engine;

import com.raktsetu.domain.model.Facility;

import java.time.LocalDate;

/**
 * How far to trust a facility's reported stock, based on when it last reported. Centres are expected to report daily
 * (e-Raktkosh requires it), but many do not.
 */
public enum Freshness {
    /** Reported today or yesterday. */
    FRESH,
    /** Reported 2-3 days ago: probably roughly right. */
    STALE,
    /** Not reported for more than 3 days, or never: do not rely on it. */
    UNVERIFIED;

    public static final int FRESH_DAYS = 1;
    public static final int STALE_DAYS = 3;

    public static Freshness of(Facility facility, LocalDate today) {
        long days = facility.daysSinceReport(today);
        if (days <= FRESH_DAYS) {
            return FRESH;
        }
        return days <= STALE_DAYS ? STALE : UNVERIFIED;
    }
}
