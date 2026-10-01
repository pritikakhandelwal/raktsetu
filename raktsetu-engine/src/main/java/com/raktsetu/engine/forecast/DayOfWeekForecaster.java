package com.raktsetu.engine.forecast;

import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.UsageHistory;

import java.time.LocalDate;

/**
 * A level times a weekly profile. Hospitals use more blood on weekdays, when planned surgery happens, than at
 * weekends; a unit expiring on Sunday faces weekend demand, not the weekday average. The level is the mean daily
 * use over the last {@code weeks} whole weeks; each weekday's index is its share of that, shrunk towards 1 when
 * there is little data (rare groups), so a few chance units on one Tuesday do not make every Tuesday look busy.
 */
public final class DayOfWeekForecaster implements DemandForecaster {

    public static final String NAME = "day-of-week";

    /** How many units' worth of evidence it takes to trust a weekday's own index halfway. */
    static final double SHRINKAGE_UNITS = 20;

    private final int weeks;

    public DayOfWeekForecaster() {
        this(8);
    }

    public DayOfWeekForecaster(int weeks) {
        if (weeks < 1) {
            throw new IllegalArgumentException("Need at least one week of history");
        }
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
        return profile(series, from.getDayOfWeek().getValue() - 1, today.getDayOfWeek().getValue() - 1);
    }

    /**
     * @param firstWeekday weekday of {@code series[0]}, 0 = Monday
     * @param todayWeekday weekday the forecast starts on, 0 = Monday
     */
    static Weekly profile(int[] series, int firstWeekday, int todayWeekday) {
        double[] sum = new double[7];
        int[] count = new int[7];
        double total = 0;
        for (int i = 0; i < series.length; i++) {
            int wd = (firstWeekday + i) % 7;
            sum[wd] += series[i];
            count[wd]++;
            total += series[i];
        }
        double level = series.length == 0 ? 0 : total / series.length;
        double[] index = new double[7];
        double trust = total / (total + SHRINKAGE_UNITS);
        for (int wd = 0; wd < 7; wd++) {
            double raw = level > 0 && count[wd] > 0 ? (sum[wd] / count[wd]) / level : 1;
            index[wd] = trust * raw + (1 - trust) * 1;
        }
        // Rescale so the indices average exactly 1: the week as a whole still totals seven days at the level.
        double mean = 0;
        for (double v : index) {
            mean += v / 7;
        }
        for (int wd = 0; wd < 7; wd++) {
            index[wd] = mean > 0 ? index[wd] / mean : 1;
        }
        return new Weekly(level, index, todayWeekday);
    }

    /** The weekly-profile forecast. */
    record Weekly(double level, double[] index, int todayWeekday) implements Forecast {

        @Override
        public double over(long days) {
            if (days <= 0) {
                return 0;
            }
            long fullWeeks = days / 7;
            double sum = fullWeeks * 7 * level;
            for (long d = 0; d < days % 7; d++) {
                sum += level * index[(int) ((todayWeekday + d) % 7)];
            }
            return sum;
        }

        @Override
        public double dailyRate() {
            return level;
        }
    }
}
