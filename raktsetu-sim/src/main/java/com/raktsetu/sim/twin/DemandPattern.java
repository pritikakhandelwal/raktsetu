package com.raktsetu.sim.twin;

import com.raktsetu.domain.model.ComponentType;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;

/**
 * How hospital demand varies over time around its average, as a multiplier on each day's expected demand.
 *
 * <p>Two effects, both widely reported for hospital transfusion but with <b>illustrative magnitudes here, to be
 * calibrated against real usage data</b>:
 * <ul>
 *   <li>a weekly profile: planned surgery happens on weekdays, so weekday demand is above the average and weekend
 *       demand well below it;</li>
 *   <li>a seasonal surge in some components, such as platelets during the post-monsoon dengue season in India
 *       (roughly August to November), modelled as a bump around a peak day of the year.</li>
 * </ul>
 * {@link #FLAT} has neither and is the default, so earlier results are unchanged.
 *
 * @param name            scenario name for results
 * @param weekday         multipliers Monday to Sunday; they average 1, so the weekly total is unchanged
 * @param surgeAmplitude  extra demand at the peak of the surge: 1.0 doubles demand
 * @param surgePeakDay    day of the year of the peak (1-366)
 * @param surgeWidthDays  standard deviation of the bump, in days
 * @param surgeComponents components affected by the surge
 */
public record DemandPattern(String name, double[] weekday, double surgeAmplitude, int surgePeakDay,
                            double surgeWidthDays, Set<ComponentType> surgeComponents) {

    public static final DemandPattern FLAT = new DemandPattern("flat", new double[] {1, 1, 1, 1, 1, 1, 1}, 0, 1, 1,
            Set.of());

    /** Weekday demand 10-15% above average, Saturday 20% and Sunday 40% below. */
    public static final DemandPattern WEEKLY = new DemandPattern("weekly",
            new double[] {1.15, 1.15, 1.10, 1.10, 1.10, 0.80, 0.60}, 0, 1, 1, Set.of());

    /** {@link #WEEKLY} plus platelet demand doubling at a peak on 30 September (sd 25 days). */
    public static final DemandPattern WEEKLY_DENGUE = new DemandPattern("weekly+dengue", WEEKLY.weekday, 1.0, 273, 25,
            Set.of(ComponentType.PLATELETS));

    public DemandPattern {
        if (weekday == null || weekday.length != 7 || Arrays.stream(weekday).anyMatch(v -> v < 0)) {
            throw new IllegalArgumentException("Need seven non-negative weekday multipliers");
        }
        double mean = Arrays.stream(weekday).average().orElse(0);
        if (Math.abs(mean - 1) > 1e-9) {
            throw new IllegalArgumentException("Weekday multipliers must average 1, not " + mean);
        }
        if (surgeAmplitude < 0 || surgeWidthDays <= 0 || surgePeakDay < 1 || surgePeakDay > 366) {
            throw new IllegalArgumentException("Invalid seasonal surge");
        }
        weekday = weekday.clone();
        surgeComponents = Set.copyOf(surgeComponents);
    }

    @Override
    public double[] weekday() {
        return weekday.clone();
    }

    /** Multiplier on the expected demand for {@code type} on {@code day}. */
    public double multiplier(LocalDate day, ComponentType type) {
        double m = weekday[day.getDayOfWeek().getValue() - 1];
        if (surgeAmplitude > 0 && surgeComponents.contains(type)) {
            int distance = Math.abs(day.getDayOfYear() - surgePeakDay);
            distance = Math.min(distance, 365 - distance); // the year wraps round
            m *= 1 + surgeAmplitude * Math.exp(-(double) distance * distance / (2 * surgeWidthDays * surgeWidthDays));
        }
        return m;
    }

    public static DemandPattern named(String name) {
        for (DemandPattern p : new DemandPattern[] {FLAT, WEEKLY, WEEKLY_DENGUE}) {
            if (p.name.equals(name)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown demand pattern '" + name + "'; use flat, weekly or weekly+dengue");
    }
}
