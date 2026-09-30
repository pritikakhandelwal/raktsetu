package com.raktsetu.engine.forecast;

import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.UsageHistory;

import java.time.LocalDate;

/**
 * The weekly profile of {@link DayOfWeekForecaster} with a level that follows change like
 * {@link ExponentialSmoothingForecaster}: each day's use is divided by its weekday index (removing the weekly
 * pattern), the result is smoothed, and the forecast puts the pattern back. In the Holt-Winters family
 * (multiplicative season, no trend), with the season estimated once per forecast rather than smoothed.
 */
public final class SeasonalSmoothingForecaster implements DemandForecaster {

    public static final String NAME = "seasonal-smoothing";

    private final double alpha;
    private final int weeks;

    public SeasonalSmoothingForecaster() {
        this(0.15, 8);
    }

    public SeasonalSmoothingForecaster(double alpha, int weeks) {
        if (alpha <= 0 || alpha > 1 || weeks < 1) {
            throw new IllegalArgumentException("alpha must be in (0, 1] and at least one week of history");
        }
        this.alpha = alpha;
        this.weeks = weeks;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Forecast forecast(UsageHistory history, String facilityId, ComponentType component, BloodGroup group,
                             LocalDate today) {
        LocalDate from = today.minusDays(7L * weeks);
        int[] series = history.dailySeries(facilityId, component, group, from, today);
        int firstWeekday = from.getDayOfWeek().getValue() - 1;
        DayOfWeekForecaster.Weekly profile = DayOfWeekForecaster.profile(series, firstWeekday,
                today.getDayOfWeek().getValue() - 1);
        double[] index = profile.index();
        double level = profile.level();
        int warm = Math.min(7, series.length);
        if (series.length > warm) {
            // Start from the first week's mean, then smooth the de-seasonalised days that follow.
            double start = 0;
            for (int i = 0; i < warm; i++) {
                start += series[i] / index[(firstWeekday + i) % 7];
            }
            level = start / warm;
            for (int i = warm; i < series.length; i++) {
                level += alpha * (series[i] / index[(firstWeekday + i) % 7] - level);
            }
        }
        return new DayOfWeekForecaster.Weekly(level, index, profile.todayWeekday());
    }
}
