# Forecasting study: method

## Question

RaktSetu decides that a unit is **at risk of expiring unused** when the demand expected at its centre before it
expires is less than the stock ahead of it, and plans transfers to centres that are **expected to use** the units.
Both decisions rest on a demand forecast. Until now that forecast was a 14-day moving average. We ask:

1. How accurately do standard forecasting methods predict a centre's use of each component and blood group over
   the horizons that matter (3 days: a platelet's remaining life; 7 days: the risk look-ahead)?
2. Does a better forecast reduce wastage or shortages when the planner relies on it?

## Forecasters (all in `raktsetu-engine/.../forecast`)

| Name | Idea | History |
|---|---|---|
| `moving-average` | Mean of the last 14 days (the previous behaviour, and the baseline) | 14 days |
| `moving-average-56` | The same over 8 weeks: a control for window length | 56 days |
| `exp-smoothing` | Simple exponential smoothing, alpha 0.15 | 56 days |
| `croston-sba` | Croston's method with the Syntetos-Boylan correction, for intermittent demand (alpha 0.1) | 84 days |
| `day-of-week` | 8-week level times weekday indices, shrunk towards 1 when data are few | 56 days |
| `seasonal-smoothing` | Weekday indices plus an exponentially smoothed de-seasonalised level (Holt-Winters family, no trend) | 56 days |

The risk rule becomes `k > D(daysLeft)`: the k-th unit to expire is at risk if the forecast demand over its
remaining days, `D`, is below k. With `moving-average`, `D(n) = u n`, exactly the previous rule; this was verified
by re-running the 30-day simulation for 5 seeds and 3 policies with byte-identical output.

## Scenarios (digital twin, `DemandPattern`)

Hospital demand is Poisson around a mean that is multiplied by a scenario pattern:

- **flat**: no pattern (the previous twin).
- **weekly**: Monday-Tuesday +15%, Wednesday-Friday +10%, Saturday -20%, Sunday -40% (averaging 1). Weekday
  concentration of transfusion because of planned surgery is widely reported; these magnitudes are illustrative.
- **weekly+dengue**: weekly, plus platelet demand doubling at a peak on 30 September (Gaussian, sd 25 days),
  representing the post-monsoon dengue season. Illustrative magnitude.

Supply is unchanged (Poisson, 10% above average demand), so the dengue scenario also tests a supply shortfall.
**All magnitudes are assumptions pending calibration against real hospital usage data.**

## Network and design

- Network: `data/networks/pune-pilot` (synthetic facilities at real towns in Pune district; OSRM road times).
- Period: 84-day warm-up from 10 May 2026, then 120 measured days (2 August to 29 November), covering the
  dengue peak.
- Reporting compliance 1.0 (every centre reports daily), isolating forecasting from stale data.
- **Accuracy**: one run per scenario with no redistribution. From weekly origins in the measured period, each
  forecaster predicts each centre's use of each component and group over 3 and 7 days, using only history
  before the origin. Mean absolute error (MAE) and mean error (bias) are reported for three slices: common
  groups (O+, B+, A+), rare groups (the other five), and platelets.
- **Effect**: 20 replications per scenario. In each, the status quo (no transfers) and expiry-first planning with
  each forecaster replay the **same random days** (common random numbers), so policy differences are paired.
  We report means over replications and the 95% confidence interval (Student t, 19 df) of each forecaster's
  paired difference from `expiry-first/moving-average`.

## Limitations

- Usage is *units issued*, not true demand: when a centre runs out, unmet demand is not recorded (censoring).
  All forecasters see the same censored data.
- Demand patterns and magnitudes are illustrative, and the network is synthetic; results show direction and
  mechanism, not calibrated Indian figures.
- Full reporting compliance; with stale reports, recent days look like zero use to every forecaster (future work:
  report-aware forecasting).

## Reproducing

```bash
./mvnw -pl raktsetu-sim -am install -DskipTests
java -cp "raktsetu-domain/target/classes;raktsetu-engine/target/classes;raktsetu-sim/target/classes;raktsetu-persistence/target/classes" \
  com.raktsetu.sim.twin.ForecastStudy data/networks/pune-pilot docs/research/forecasting 20
```

(Use `:` instead of `;` between classpath entries on macOS and Linux.) Results: `results.md`, `accuracy.csv`,
`runs.csv` in this folder.
