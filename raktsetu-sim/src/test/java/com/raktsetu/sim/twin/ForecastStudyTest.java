package com.raktsetu.sim.twin;

import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.engine.forecast.DayOfWeekForecaster;
import com.raktsetu.engine.forecast.MovingAverageForecaster;
import com.raktsetu.engine.strategy.ExpiryFirstStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ForecastStudyTest {

    @Test
    void demandPatternsAverageOneOverAWeek() {
        LocalDate monday = LocalDate.of(2026, 10, 5);
        for (DemandPattern p : List.of(DemandPattern.FLAT, DemandPattern.WEEKLY)) {
            double week = 0;
            for (int d = 0; d < 7; d++) {
                week += p.multiplier(monday.plusDays(d), ComponentType.RED_CELLS);
            }
            assertThat(week).isCloseTo(7, within(1e-9));
        }
        assertThat(DemandPattern.WEEKLY.multiplier(monday.plusDays(6), ComponentType.RED_CELLS)).isEqualTo(0.6);
        assertThat(DemandPattern.FLAT.weekday()).containsOnly(1.0);
    }

    @Test
    void theDengueSurgePeaksAtTheEndOfSeptemberForPlateletsOnly() {
        LocalDate peak = LocalDate.ofYearDay(2026, 273);
        DemandPattern d = DemandPattern.WEEKLY_DENGUE;
        double weekday = DemandPattern.WEEKLY.multiplier(peak, ComponentType.PLATELETS);
        assertThat(d.multiplier(peak, ComponentType.PLATELETS)).isCloseTo(2 * weekday, within(1e-9));
        assertThat(d.multiplier(peak, ComponentType.RED_CELLS)).isEqualTo(weekday);
        LocalDate march = LocalDate.of(2026, 3, 30);
        assertThat(d.multiplier(march, ComponentType.PLATELETS))
                .isCloseTo(DemandPattern.WEEKLY.multiplier(march, ComponentType.PLATELETS), within(1e-6));
        assertThat(DemandPattern.named("weekly+dengue")).isSameAs(d);
        assertThatThrownBy(() -> DemandPattern.named("monsoon")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidPatternsAreRejected() {
        assertThatThrownBy(() -> new DemandPattern("x", new double[] {1, 1, 1}, 0, 1, 1, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DemandPattern("x", new double[] {2, 2, 2, 2, 2, 2, 2}, 0, 1, 1, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DemandPattern("x", new double[] {1, 1, 1, 1, 1, 1, 1}, -1, 1, 1, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void flatDemandGivesTheSameScheduleAsBefore() {
        NetworkSpec spec = TwinSimulatorTest.smallSpec();
        LocalDate start = LocalDate.of(2026, 1, 1);
        Schedule old = Schedule.generate(spec, new TwinSettings(start, 0, 30, 1.0, 42, 1));
        Schedule flat = Schedule.generate(spec, new TwinSettings(start, 0, 30, 1.0, 42, 1, DemandPattern.FLAT));
        for (int d = 0; d < 30; d++) {
            assertThat(flat.day(d).requests()).isEqualTo(old.day(d).requests());
        }
        // A weekly pattern changes weekend demand.
        Schedule weekly = Schedule.generate(spec, new TwinSettings(start, 0, 70, 1.0, 42, 1, DemandPattern.WEEKLY));
        int weekdays = 0;
        int sundays = 0;
        for (int d = 0; d < 70; d++) {
            int units = weekly.day(d).requests().stream().mapToInt(r -> r.quantity()).sum();
            if (start.plusDays(d).getDayOfWeek().getValue() == 7) {
                sundays += units;
            } else if (start.plusDays(d).getDayOfWeek().getValue() <= 5) {
                weekdays += units;
            }
        }
        assertThat(sundays / 10.0).isLessThan(weekdays / 50.0 * 0.7);
    }

    @Test
    void policiesAreNamedAfterStrategyAndForecaster() {
        assertThat(TwinPolicy.strategy(ExpiryFirstStrategy.NAME, new DayOfWeekForecaster()).name())
                .isEqualTo("expiry-first/day-of-week");
        assertThat(TwinPolicy.strategy(ExpiryFirstStrategy.NAME, new MovingAverageForecaster(56)).name())
                .isEqualTo("expiry-first/moving-average-56");
    }

    @Test
    void statisticsHelpers() {
        assertThat(ForecastStudy.mean(new double[] {1, 2, 3})).isEqualTo(2);
        assertThat(ForecastStudy.sd(new double[] {2, 4, 4, 4, 5, 5, 7, 9})).isCloseTo(2.138, within(1e-3));
        assertThat(ForecastStudy.sd(new double[] {1})).isZero();
        assertThat(ForecastStudy.tQuantile(9)).isEqualTo(2.262);
        assertThat(ForecastStudy.tQuantile(100)).isEqualTo(1.96);
        assertThat(ForecastStudy.tQuantile(0)).isZero();
        assertThat(ForecastStudy.inSlice("common", ComponentType.PLASMA, BloodGroup.O_POS)).isTrue();
        assertThat(ForecastStudy.inSlice("rare", ComponentType.PLASMA, BloodGroup.O_POS)).isFalse();
        assertThat(ForecastStudy.inSlice("platelets", ComponentType.PLATELETS, BloodGroup.AB_NEG)).isTrue();
        assertThatThrownBy(() -> ForecastStudy.inSlice("x", ComponentType.PLASMA, BloodGroup.O_POS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theStudyRunsEndToEndAndWritesItsReport(@TempDir Path out) throws Exception {
        NetworkSpec spec = TwinSimulatorTest.smallSpec();
        var accuracy = ForecastStudy.accuracy(spec);
        assertThat(accuracy).hasSize(3 * 2 * 3 * ForecastStudy.forecasters().size());
        assertThat(accuracy).allSatisfy(a -> assertThat(a.mae()).isNotNegative());
        List<String[]> runs = new ArrayList<>();
        var effects = ForecastStudy.downstream(spec, 2, runs);
        assertThat(runs).hasSize(3 * 2 * (ForecastStudy.forecasters().size() + 1));
        // The baseline's difference from itself is exactly zero.
        assertThat(effects).filteredOn(e -> e.forecaster().equals("expiry-first/moving-average"))
                .allSatisfy(e -> assertThat(e.diffMean()).isZero());
        String md = ForecastStudy.report(spec, 2, accuracy, effects);
        assertThat(md).contains("## 1. Forecast accuracy", "### Scenario: weekly+dengue", "moving-average-56");
        Files.writeString(out.resolve("r.md"), md);
    }
}
