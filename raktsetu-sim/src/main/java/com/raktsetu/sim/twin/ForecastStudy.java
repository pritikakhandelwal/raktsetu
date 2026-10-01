package com.raktsetu.sim.twin;

import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.UsageHistory;
import com.raktsetu.engine.forecast.CrostonForecaster;
import com.raktsetu.engine.forecast.DayOfWeekForecaster;
import com.raktsetu.engine.forecast.DemandForecaster;
import com.raktsetu.engine.forecast.ExponentialSmoothingForecaster;
import com.raktsetu.engine.forecast.MovingAverageForecaster;
import com.raktsetu.engine.forecast.SeasonalSmoothingForecaster;
import com.raktsetu.engine.strategy.ExpiryFirstStrategy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The forecasting study: how accurately each {@link DemandForecaster} predicts centres' use, and what that does to
 * wastage and shortages when the planner relies on it. Runs on a network folder (for example
 * {@code data/networks/pune-pilot}) under three demand scenarios and writes CSV files and a Markdown summary.
 *
 * <pre>java ... com.raktsetu.sim.twin.ForecastStudy data/networks/pune-pilot docs/research/forecasting 10</pre>
 */
public final class ForecastStudy {

    /** Measure August to November, so the dengue peak (30 September) sits in the middle. */
    static final LocalDate START = LocalDate.of(2026, 5, 10);
    static final int WARMUP_DAYS = 84;
    static final int MEASURED_DAYS = 120;
    static final long SEED = 20260927L;
    static final Set<BloodGroup> COMMON = Set.of(BloodGroup.O_POS, BloodGroup.B_POS, BloodGroup.A_POS);
    static final int[] HORIZONS = {3, 7};
    /** Common groups (O+, B+, A+), rare groups (the other five), and platelets alone (short-lived; the dengue surge). */
    static final List<String> SLICES = List.of("common", "rare", "platelets");

    static boolean inSlice(String slice, ComponentType type, BloodGroup g) {
        return switch (slice) {
            case "common" -> COMMON.contains(g);
            case "rare" -> !COMMON.contains(g);
            case "platelets" -> type == ComponentType.PLATELETS;
            default -> throw new IllegalArgumentException(slice);
        };
    }

    static List<DemandForecaster> forecasters() {
        // moving-average-56 learns from as many days as day-of-week: it separates "a longer window" from
        // "modelling the week".
        return List.of(new MovingAverageForecaster(), new MovingAverageForecaster(56),
                new ExponentialSmoothingForecaster(), new CrostonForecaster(), new DayOfWeekForecaster(),
                new SeasonalSmoothingForecaster());
    }

    static List<DemandPattern> scenarios() {
        return List.of(DemandPattern.FLAT, DemandPattern.WEEKLY, DemandPattern.WEEKLY_DENGUE);
    }

    /** Absolute and signed errors of one forecaster on one slice. */
    record Accuracy(String scenario, String forecaster, int horizon, String groups, long n, double mae, double bias,
                    double actualMean) {
        double relative(Accuracy baseline) {
            return baseline.mae == 0 ? 1 : mae / baseline.mae;
        }
    }

    /** A downstream metric's paired difference from the baseline forecaster, over replications. */
    record Effect(String scenario, String forecaster, String metric, double mean, double baselineMean,
                  double diffMean, double diffLow, double diffHigh) {
    }

    public static void main(String[] args) throws Exception {
        Path network = Path.of(args.length > 0 ? args[0] : "data/networks/pune-pilot");
        Path out = Path.of(args.length > 1 ? args[1] : "docs/research/forecasting");
        int replications = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        NetworkSpec spec = NetworkSpecs.load(network);
        Files.createDirectories(out);

        long t0 = System.nanoTime();
        List<Accuracy> accuracy = accuracy(spec);
        System.out.printf(Locale.ROOT, "Accuracy done in %.0f s%n", (System.nanoTime() - t0) / 1e9);
        List<String[]> runs = new ArrayList<>();
        List<Effect> effects = downstream(spec, replications, runs);
        System.out.printf(Locale.ROOT, "Downstream done in %.0f s%n", (System.nanoTime() - t0) / 1e9);

        writeCsv(out.resolve("accuracy.csv"), List.of("scenario", "forecaster", "horizon_days", "groups", "n",
                "mae", "bias", "actual_mean"), accuracy.stream().map(a -> new String[] {a.scenario, a.forecaster,
                String.valueOf(a.horizon), a.groups, String.valueOf(a.n), f(a.mae), f(a.bias), f(a.actualMean)})
                .toList());
        writeCsv(out.resolve("runs.csv"), List.of("scenario", "policy", "replication", "expired", "expired_platelets",
                "units_unmet", "units_requested", "units_moved", "transfer_km"), runs);
        String md = report(spec, replications, accuracy, effects);
        Files.writeString(out.resolve("results.md"), md);
        System.out.println(md);
    }

    // ------------------------------------------------------------------ accuracy

    static List<Accuracy> accuracy(NetworkSpec spec) throws RaktSetuException {
        List<Accuracy> result = new ArrayList<>();
        for (DemandPattern scenario : scenarios()) {
            TwinSettings settings = new TwinSettings(START, WARMUP_DAYS, MEASURED_DAYS, 1.0, SEED, 1, scenario);
            TwinSimulator.Run run = new TwinSimulator(spec, settings).run(TwinPolicy.NONE, false);
            UsageHistory usage = run.truth().usage();
            List<String> centres = spec.centres().stream().map(Site::id).toList();
            for (int horizon : HORIZONS) {
                for (String groups : SLICES) {
                    for (DemandForecaster forecaster : forecasters()) {
                        result.add(accuracy(scenario, forecaster, horizon, groups, usage, centres));
                    }
                }
            }
        }
        return result;
    }

    private static Accuracy accuracy(DemandPattern scenario, DemandForecaster forecaster, int horizon, String groups,
                                     UsageHistory usage, List<String> centres) {
        long n = 0;
        double absError = 0;
        double error = 0;
        double actualSum = 0;
        LocalDate firstOrigin = START.plusDays(WARMUP_DAYS);
        LocalDate end = START.plusDays(WARMUP_DAYS + MEASURED_DAYS);
        for (LocalDate origin = firstOrigin; !origin.plusDays(horizon).isAfter(end); origin = origin.plusDays(7)) {
            for (String centre : centres) {
                for (ComponentType type : ComponentType.values()) {
                    for (BloodGroup g : BloodGroup.values()) {
                        if (!inSlice(groups, type, g)) {
                            continue;
                        }
                        double predicted = forecaster.forecast(usage, centre, type, g, origin).over(horizon);
                        int actual = 0;
                        for (int u : usage.dailySeries(centre, type, g, origin, origin.plusDays(horizon))) {
                            actual += u;
                        }
                        n++;
                        absError += Math.abs(predicted - actual);
                        error += predicted - actual;
                        actualSum += actual;
                    }
                }
            }
        }
        return new Accuracy(scenario.name(), forecaster.name(), horizon, groups, n, absError / n, error / n,
                actualSum / n);
    }

    // ------------------------------------------------------------------ downstream

    static final List<String> METRICS = List.of("expired", "expired_platelets", "units_unmet", "units_moved",
            "transfer_km");

    static List<Effect> downstream(NetworkSpec spec, int replications, List<String[]> runs)
            throws RaktSetuException {
        List<Effect> effects = new ArrayList<>();
        for (DemandPattern scenario : scenarios()) {
            // values[policy][metric][replication]
            Map<String, double[][]> values = new LinkedHashMap<>();
            List<TwinPolicy> policies = new ArrayList<>();
            policies.add(TwinPolicy.NONE);
            for (DemandForecaster f : forecasters()) {
                policies.add(TwinPolicy.strategy(ExpiryFirstStrategy.NAME, f));
            }
            for (TwinPolicy p : policies) {
                values.put(p.name(), new double[METRICS.size()][replications]);
            }
            for (int r = 1; r <= replications; r++) {
                TwinSimulator sim = new TwinSimulator(spec,
                        new TwinSettings(START, WARMUP_DAYS, MEASURED_DAYS, 1.0, SEED, r, scenario));
                for (TwinPolicy p : policies) {
                    TwinResult res = sim.run(p);
                    double[] m = {res.unitsExpired(), res.expired(ComponentType.PLATELETS), res.unitsUnmet(),
                        res.transferUnitsMoved(), res.transferKm()};
                    for (int k = 0; k < m.length; k++) {
                        values.get(p.name())[k][r - 1] = m[k];
                    }
                    runs.add(new String[] {scenario.name(), p.name(), String.valueOf(r), i(m[0]), i(m[1]), i(m[2]),
                        String.valueOf(res.unitsRequested()), i(m[3]), i(m[4])});
                }
                System.out.printf(Locale.ROOT, "  %s replication %d done%n", scenario.name(), r);
            }
            String baseline = TwinPolicy.strategy(ExpiryFirstStrategy.NAME, new MovingAverageForecaster()).name();
            for (TwinPolicy p : policies) {
                for (int k = 0; k < METRICS.size(); k++) {
                    double[] x = values.get(p.name())[k];
                    double[] b = values.get(baseline)[k];
                    double[] diff = new double[replications];
                    for (int r = 0; r < replications; r++) {
                        diff[r] = x[r] - b[r];
                    }
                    double half = tQuantile(replications - 1) * sd(diff) / Math.sqrt(replications);
                    effects.add(new Effect(scenario.name(), p.name(), METRICS.get(k), mean(x), mean(b), mean(diff),
                            mean(diff) - half, mean(diff) + half));
                }
            }
        }
        return effects;
    }

    // ------------------------------------------------------------------ report

    static String report(NetworkSpec spec, int replications, List<Accuracy> accuracy, List<Effect> effects) {
        StringBuilder md = new StringBuilder();
        md.append("# Forecasting study: results\n\n");
        md.append(String.format(Locale.ROOT, "Network `%s` (%d centres, %d hospitals). Measured %s to %s after a "
                        + "%d-day warm-up; full reporting compliance. Seed %d. Generated by `ForecastStudy`.%n%n",
                spec.name(), spec.centres().size(), spec.hospitals().size(), START.plusDays(WARMUP_DAYS),
                START.plusDays(WARMUP_DAYS + MEASURED_DAYS - 1), WARMUP_DAYS, SEED));

        md.append("## 1. Forecast accuracy\n\n");
        md.append("Mean absolute error of the units a centre uses over the next 3 and 7 days, per centre, component "
                + "and blood group, from weekly forecast origins. Relative MAE < 1 beats the moving average. "
                + "Slices: common groups (O+, B+, A+, all components), rare groups (the other five), and platelets "
                + "(all groups). `moving-average-56` uses the same 8 weeks of history as `day-of-week`, separating "
                + "the effect of a longer window from that of modelling the week.\n\n");
        for (int horizon : HORIZONS) {
            md.append(String.format(Locale.ROOT, "### %d-day horizon%n%n", horizon));
            md.append("| Scenario | Groups | Actual mean | ");
            forecasters().forEach(f -> md.append(f.name()).append(" | "));
            md.append("\n|---|---|---:|");
            forecasters().forEach(f -> md.append("---:|"));
            md.append('\n');
            for (DemandPattern s : scenarios()) {
                for (String groups : SLICES) {
                    List<Accuracy> row = accuracy.stream().filter(a -> a.scenario.equals(s.name())
                            && a.horizon == horizon && a.groups.equals(groups)).toList();
                    Accuracy base = row.get(0);
                    md.append(String.format(Locale.ROOT, "| %s | %s | %.2f | ", s.name(), groups, base.actualMean));
                    for (Accuracy a : row) {
                        md.append(String.format(Locale.ROOT, "%.3f (%.2f) | ", a.mae, a.relative(base)));
                    }
                    md.append('\n');
                }
            }
            md.append("\nCells: MAE (relative to moving average).\n\n");
        }

        md.append("## 2. Effect on the network (expiry-first planning)\n\n");
        md.append(String.format(Locale.ROOT, "%d replications per scenario; every policy replays the same random "
                + "days (common random numbers), so differences are paired. Values are totals over the measured "
                + "period, averaged over replications; brackets give the 95%% confidence interval of the "
                + "difference from `expiry-first/moving-average`.%n%n", replications));
        for (DemandPattern s : scenarios()) {
            md.append(String.format(Locale.ROOT, "### Scenario: %s%n%n", s.name()));
            md.append("| Policy | Expired | Expired platelets | Units unmet | Units moved | Transfer km |\n");
            md.append("|---|---:|---:|---:|---:|---:|\n");
            List<String> policies = effects.stream().filter(e -> e.scenario.equals(s.name()))
                    .map(Effect::forecaster).distinct().toList();
            for (String p : policies) {
                md.append("| ").append(p).append(" | ");
                for (String metric : METRICS) {
                    Effect e = effects.stream().filter(x -> x.scenario.equals(s.name()) && x.forecaster.equals(p)
                            && x.metric.equals(metric)).findFirst().orElseThrow();
                    if (p.endsWith("/" + MovingAverageForecaster.NAME)) {
                        md.append(String.format(Locale.ROOT, "%.1f | ", e.mean));
                    } else {
                        md.append(String.format(Locale.ROOT, "%.1f [%+.1f, %+.1f] | ", e.mean, e.diffLow,
                                e.diffHigh));
                    }
                }
                md.append('\n');
            }
            md.append('\n');
        }
        return md.toString();
    }

    // ------------------------------------------------------------------ helpers

    static double mean(double[] x) {
        double s = 0;
        for (double v : x) {
            s += v;
        }
        return x.length == 0 ? 0 : s / x.length;
    }

    static double sd(double[] x) {
        if (x.length < 2) {
            return 0;
        }
        double m = mean(x);
        double s = 0;
        for (double v : x) {
            s += (v - m) * (v - m);
        }
        return Math.sqrt(s / (x.length - 1));
    }

    /** Two-sided 95% Student-t quantile for {@code df} degrees of freedom. */
    static double tQuantile(int df) {
        double[] table = {12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228, 2.201, 2.179,
            2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086, 2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052,
            2.048, 2.045, 2.042};
        if (df < 1) {
            return 0;
        }
        return df <= table.length ? table[df - 1] : 1.96;
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private static String i(double v) {
        return String.valueOf((long) v);
    }

    private static void writeCsv(Path file, List<String> header, List<String[]> rows) throws IOException {
        StringBuilder sb = new StringBuilder(String.join(",", header)).append('\n');
        for (String[] r : rows) {
            sb.append(String.join(",", r)).append('\n');
        }
        Files.writeString(file, sb.toString());
    }
}
