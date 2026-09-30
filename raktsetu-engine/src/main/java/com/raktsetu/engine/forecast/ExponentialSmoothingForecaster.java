package com.raktsetu.engine.forecast;

import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.UsageHistory;

import java.time.LocalDate;

/**
 * Simple exponential smoothing: each day moves the level a fraction {@code alpha} towards what was used, so recent
 * days count most and a change in demand is followed within a few days. Learns from the last {@code history} days.
 */
public final class ExponentialSmoothingForecaster implements DemandForecaster {

    public static final String NAME = "exp-smoothing";

    private final double alpha;
    private final int historyDays;

    public ExponentialSmoothingForecaster() {
        this(0.15, 56);
    }

    public ExponentialSmoothingForecaster(double alpha, int historyDays) {
        if (alpha <= 0 || alpha > 1 || historyDays < 7) {
            throw new IllegalArgumentException("alpha must be in (0, 1] and history at least 7 days");
        }
        this.alpha = alpha;
        this.historyDays = historyDays;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Forecast forecast(UsageHistory history, String facilityId, ComponentType component, BloodGroup group,
                             LocalDate today) {
        int[] series = history.dailySeries(facilityId, component, group, today.minusDays(historyDays), today);
        return new Flat(level(series, alpha));
    }

    /** The smoothed level after the whole series, starting from the mean of its first week. */
    static double level(int[] series, double alpha) {
        double level = 0;
        int warm = Math.min(7, series.length);
        for (int i = 0; i < warm; i++) {
            level += series[i];
        }
        level = warm == 0 ? 0 : level / warm;
        for (int i = warm; i < series.length; i++) {
            level += alpha * (series[i] - level);
        }
        return level;
    }
}
