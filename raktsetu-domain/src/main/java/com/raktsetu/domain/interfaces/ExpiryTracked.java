package com.raktsetu.domain.interfaces;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Something with an expiry date.
 *
 * <p>Convention used everywhere in RaktSetu: an item is expired <em>on</em> its expiry date, not the day after.
 * "Today" is always passed in by the caller so tests, the demo and the simulator control the date.
 */
public interface ExpiryTracked {

    LocalDate getExpiryDate();

    /** Whole days from {@code today} until expiry; zero or negative once expired. */
    default long daysToExpiry(LocalDate today) {
        return ChronoUnit.DAYS.between(today, getExpiryDate());
    }

    default boolean isExpired(LocalDate today) {
        return !today.isBefore(getExpiryDate());
    }
}
