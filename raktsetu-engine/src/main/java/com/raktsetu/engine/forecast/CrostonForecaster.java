package com.raktsetu.engine.forecast;

import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.UsageHistory;

import java.time.LocalDate;

/**
 * Croston's method with the Syntetos-Boylan correction (SBA), the standard forecast for <em>intermittent</em>
 * demand: series that are zero on most days, such as rare blood groups (AB-, B-) at a small centre. Averaging such
 * a series mixes the size of a demand with how often it comes; Croston smooths the two separately (the size of each
 * non-zero demand, and the gap between them) and forecasts their ratio. SBA multiplies by {@code 1 - alpha/2},
 * removing Croston's known upward bias.
 *
 * <p>References: J. D. Croston, "Forecasting and stock control for intermittent demands", Operational Research
 * Quarterly 23 (1972); A. A. Syntetos and J. E. Boylan, "The accuracy of intermittent demand estimates",
 * International Journal of Forecasting 21 (2005).
 */
public final class CrostonForecaster implements DemandForecaster {

    public static final String NAME = "croston-sba";

    private final double alpha;
    private final int historyDays;

    public CrostonForecaster() {
        this(0.1, 84);
    }

    public CrostonForecaster(double alpha, int historyDays) {
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
        return new Flat(rate(series, alpha));
    }

    /** Expected units per day. */
    static double rate(int[] series, double alpha) {
        double size = -1;      // smoothed size of a non-zero demand
        double interval = -1;  // smoothed days between non-zero demands
        int sinceLast = 0;
        for (int units : series) {
            sinceLast++;
            if (units > 0) {
                if (size < 0) {
                    size = units;
                    interval = sinceLast;
                } else {
                    size += alpha * (units - size);
                    interval += alpha * (sinceLast - interval);
                }
                sinceLast = 0;
            }
        }
        if (size < 0) {
            return 0; // never used in the history
        }
        return (1 - alpha / 2) * size / interval;
    }
}
