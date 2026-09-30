package com.raktsetu.engine.forecast;

import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.UsageHistory;

import java.time.LocalDate;

/**
 * The baseline: the average of the last {@code window} days, assumed to continue. It is what RaktSetu used before
 * forecasting was added, and what hospitals commonly do by hand.
 */
public final class MovingAverageForecaster implements DemandForecaster {

    public static final String NAME = "moving-average";

    private final int window;

    public MovingAverageForecaster() {
        this(UsageHistory.DEFAULT_WINDOW_DAYS);
    }

    public MovingAverageForecaster(int window) {
        if (window < 1) {
            throw new IllegalArgumentException("Window must be at least one day");
        }
        this.window = window;
    }

    /** {@code moving-average} for the default 14 days, otherwise {@code moving-average-56} and so on. */
    @Override
    public String name() {
        return window == UsageHistory.DEFAULT_WINDOW_DAYS ? NAME : NAME + "-" + window;
    }

    @Override
    public Forecast forecast(UsageHistory history, String facilityId, ComponentType component, BloodGroup group,
                             LocalDate today) {
        return new Flat(history.averageDaily(facilityId, component, group, today, window));
    }
}
