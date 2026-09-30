package com.raktsetu.engine.forecast;

import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.UsageHistory;

import java.time.LocalDate;

/**
 * Predicts how many units of one component and blood group a facility will use. The expiry-risk check asks "will
 * these units be used before they expire?" and the planner asks "how many more could this centre use?"; both are
 * questions about demand over the next few days, which is what a forecaster answers.
 *
 * <p>Implementations learn from the usage recorded before {@code today}. {@link #forecast} is called once per
 * facility, component and group; the returned {@link Forecast} answers any horizon cheaply.
 */
public interface DemandForecaster {

    /** A short name for results tables, for example {@code moving-average}. */
    String name();

    Forecast forecast(UsageHistory history, String facilityId, ComponentType component, BloodGroup group,
                      LocalDate today);

    /** Demand expected from today onwards. */
    interface Forecast {

        /** Expected units used over the {@code days} days starting today ({@code days >= 0}). */
        double over(long days);

        /** Expected units per day over the coming week, for display. */
        double dailyRate();
    }

    /** A forecast of a constant daily rate. */
    record Flat(double rate) implements Forecast {
        public Flat {
            if (rate < 0 || Double.isNaN(rate)) {
                throw new IllegalArgumentException("A demand rate cannot be negative");
            }
        }

        @Override
        public double over(long days) {
            return rate * days;
        }

        @Override
        public double dailyRate() {
            return rate;
        }
    }
}
