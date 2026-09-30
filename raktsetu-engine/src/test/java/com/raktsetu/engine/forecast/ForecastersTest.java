package com.raktsetu.engine.forecast;

import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.UsageHistory;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.function.IntUnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ForecastersTest {

    /** A Monday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final ComponentType RBC = ComponentType.RED_CELLS;
    private static final BloodGroup G = BloodGroup.O_POS;

    /** {@code days} days of history before TODAY; {@code units(i)} for day i, where i = 0 is the oldest. */
    private static UsageHistory history(int days, IntUnaryOperator units) {
        UsageHistory h = new UsageHistory();
        for (int i = 0; i < days; i++) {
            h.record(TODAY.minusDays(days - i), "SC01", RBC, G, units.applyAsInt(i));
        }
        return h;
    }

    private static boolean weekend(LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    @Test
    void dailySeriesFillsGapsWithZeros() {
        UsageHistory h = new UsageHistory();
        h.record(TODAY.minusDays(3), "SC01", RBC, G, 4);
        h.record(TODAY.minusDays(1), "SC01", RBC, G, 2);
        assertThat(h.dailySeries("SC01", RBC, G, TODAY.minusDays(4), TODAY)).containsExactly(0, 4, 0, 2);
        assertThat(h.dailySeries("SC99", RBC, G, TODAY.minusDays(2), TODAY)).containsExactly(0, 0);
    }

    @Test
    void movingAverageIsTheOldRuleExactly() {
        UsageHistory h = history(30, i -> i % 3);
        var f = new MovingAverageForecaster().forecast(h, "SC01", RBC, G, TODAY);
        double avg = h.averageDaily("SC01", RBC, G, TODAY);
        assertThat(f.dailyRate()).isEqualTo(avg);
        assertThat(f.over(5)).isEqualTo(avg * 5);
        assertThatThrownBy(() -> new MovingAverageForecaster(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exponentialSmoothingFollowsAChangeFasterThanAFortnightAverage() {
        // 42 days at 2 a day, then a week at 8 a day (an outbreak).
        UsageHistory h = history(49, i -> i < 42 ? 2 : 8);
        double ses = new ExponentialSmoothingForecaster().forecast(h, "SC01", RBC, G, TODAY).dailyRate();
        double ma = new MovingAverageForecaster().forecast(h, "SC01", RBC, G, TODAY).dailyRate();
        assertThat(ma).isEqualTo(5.0);            // (7 x 2 + 7 x 8) / 14
        assertThat(ses).isGreaterThan(6.0).isLessThan(8.0);
        assertThat(ExponentialSmoothingForecaster.level(new int[] {3, 3, 3, 3, 3, 3, 3, 3, 3}, 0.3))
                .isCloseTo(3.0, within(1e-12));
        assertThat(ExponentialSmoothingForecaster.level(new int[0], 0.3)).isZero();
    }

    @Test
    void crostonSeparatesHowMuchFromHowOften() {
        // 3 units every 4th day: a rare group. True rate 0.75 a day.
        int[] series = new int[40];
        for (int i = 3; i < 40; i += 4) {
            series[i] = 3;
        }
        // Size stays 3 and interval 4 after the first demand, so SBA = (1 - 0.1/2) * 3 / 4.
        assertThat(CrostonForecaster.rate(series, 0.1)).isCloseTo(0.95 * 0.75, within(1e-12));
        assertThat(CrostonForecaster.rate(new int[10], 0.1)).isZero();
        // Hand-checked smoothing: sizes 2 then 4, gaps 1 then 3, alpha 0.5.
        // size = 2 + 0.5 (4 - 2) = 3, interval = 1 + 0.5 (3 - 1) = 2; SBA = 0.75 * 3 / 2.
        assertThat(CrostonForecaster.rate(new int[] {2, 0, 0, 4}, 0.5)).isCloseTo(1.125, within(1e-12));
    }

    @Test
    void dayOfWeekKnowsWeekendsAreQuiet() {
        // Ten a day on weekdays, two at weekends, for eight weeks.
        UsageHistory h = history(56, i -> weekend(TODAY.minusDays(56 - i)) ? 2 : 10);
        var f = new DayOfWeekForecaster().forecast(h, "SC01", RBC, G, TODAY);
        assertThat(f.over(7)).isCloseTo(54, within(0.5));            // a whole week, whatever the day
        assertThat(f.over(5)).isCloseTo(50, within(1.5));            // Monday to Friday: weekdays only
        var saturday = new DayOfWeekForecaster().forecast(h, "SC01", RBC, G, TODAY.minusDays(2));
        assertThat(saturday.over(2)).isLessThan(8);                   // the weekend ahead is quiet
        assertThat(f.over(0)).isZero();
        assertThat(f.dailyRate()).isCloseTo(54.0 / 7, within(1e-9));
    }

    @Test
    void aFewUnitsDoNotMakeAWeekdayProfile() {
        // Three units in eight weeks, all on Tuesdays: too little to believe Tuesdays are special.
        UsageHistory h = history(56, i -> i == 1 || i == 8 || i == 15 ? 1 : 0);
        var f = new DayOfWeekForecaster().forecast(h, "SC01", RBC, G, TODAY);
        double[] index = ((DayOfWeekForecaster.Weekly) f).index();
        assertThat(index[1]).isLessThan(2.5);  // unshrunk, Tuesday's index would be 7
        assertThat(new DayOfWeekForecaster().forecast(new UsageHistory(), "SC01", RBC, G, TODAY).over(7)).isZero();
    }

    @Test
    void seasonalSmoothingKeepsTheWeekAndFollowsTheLevel() {
        // Weekday/weekend pattern whose level doubles in the last two weeks.
        UsageHistory h = history(56, i -> {
            int base = weekend(TODAY.minusDays(56 - i)) ? 2 : 10;
            return i >= 42 ? base * 2 : base;
        });
        var seasonal = new SeasonalSmoothingForecaster().forecast(h, "SC01", RBC, G, TODAY);
        var dow = new DayOfWeekForecaster().forecast(h, "SC01", RBC, G, TODAY);
        assertThat(seasonal.over(7)).isGreaterThan(dow.over(7));     // has noticed the rise
        assertThat(seasonal.over(5)).isGreaterThan(seasonal.over(7) * 5 / 7); // weekdays still busier
    }

    @Test
    void flatForecastsRejectNonsense() {
        assertThatThrownBy(() -> new DemandForecaster.Flat(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExponentialSmoothingForecaster(0, 56)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CrostonForecaster(0.1, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DayOfWeekForecaster(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SeasonalSmoothingForecaster(2, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new DemandForecaster.Flat(2).over(3)).isEqualTo(6);
    }
}
