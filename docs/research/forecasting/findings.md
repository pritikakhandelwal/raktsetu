# Forecasting study: findings

Method: [`method.md`](method.md). Full tables: [`results.md`](results.md); raw data: `accuracy.csv`, `runs.csv`.
All figures are from the synthetic Pune pilot network with illustrative demand patterns; they show direction and
mechanism, not calibrated Indian outcomes.

## 1. Redistribution is the effect that matters

Expiry-first redistribution with the baseline forecaster, against the status quo (no transfers), over 120 days and
20 paired replications:

| Scenario | Expired units: none → expiry-first | Change (95% CI of difference) | Unmet demand: none → expiry-first |
|---|---:|---:|---:|
| flat | 1,890 → 750 | **-60%** (-1,179 to -1,100 units) | 2.13% → 1.02% of units requested |
| weekly | 1,913 → 757 | **-60%** (-1,190 to -1,122) | 2.09% → 1.02% |
| weekly + dengue | 1,509 → 342 | **-77%** (-1,196 to -1,139) | 9.81% → 8.53% |

Moving stock that is about to expire to centres that can use it cuts wastage by about 60% and halves shortages
when supply is adequate. In the dengue scenario platelet demand doubles while supply does not, so most of the
shortage is a supply problem that no redistribution (or forecast) can remove: unmet demand falls only from 9.8%
to 8.5%.

## 2. Longer history improves forecasts; modelling the week adds almost nothing at this granularity

Mean absolute error relative to the 14-day moving average (lower is better):

| Forecaster | 3-day horizon, common groups (flat / weekly) | 7-day horizon, common groups (flat / weekly) |
|---|---:|---:|
| moving-average (14 days) | 1.00 / 1.00 | 1.00 / 1.00 |
| moving-average-56 | 0.94 / 0.94 | 0.88 / 0.87 |
| day-of-week | 0.97 / 0.93 | 0.88 / 0.87 |
| seasonal-smoothing | 1.03 / 0.98 | 1.02 / 1.01 |
| exp-smoothing | 1.02 / 1.02 | 1.03 / 1.03 |
| croston-sba | 0.97 / 0.98 | 0.93 / 0.93 |

- The 56-day average is more accurate than the 14-day one in **every** scenario, including flat demand (5-13% for
  common groups and platelets, 2-9% for rare groups): a longer window averages out more Poisson noise. This control is essential: without it, `day-of-week` appears to
  "win" by 12% on flat demand, where there is no weekly pattern to find.
- Once window length is controlled for, modelling the weekday gains about **1 percentage point** at the 3-day
  horizon under a weekly pattern (0.93 against 0.94), and exactly nothing at 7 days, where every horizon contains
  one of each weekday. One centre's use of one component and group over three days averages about 4 units for
  common groups, so Poisson noise (standard deviation about 2) swamps a ±15-40% weekday effect, and the weekday
  indices themselves are estimated from noisy counts.
- **Croston/SBA does not help rare groups** (relative MAE 1.10 at 3 days, with a positive bias). With a constant
  underlying rate, a plain average is already near the best possible; Croston's advantage appears when demand
  sizes and intervals vary, which this demand model does not produce.

## 3. A more accurate forecast did not give a better plan

Paired differences from `expiry-first/moving-average`, flat scenario (the weekly and dengue scenarios agree):

| Forecaster | 7-day MAE (relative) | Expired units (95% CI of difference) | Units moved |
|---|---:|---:|---:|
| moving-average-56 | 0.88 (more accurate) | **+13.7** (+8.4 to +19.1) | -88 |
| day-of-week | 0.88 (more accurate) | **+14.1** (+7.5 to +20.8) | -83 |
| croston-sba | 0.93 | +5.7 (+0.7 to +10.8) | +29 |
| seasonal-smoothing | 1.02 | **-15.6** (-18.3 to -12.8) | +72 |
| exp-smoothing | 1.03 (less accurate) | **-17.1** (-20.8 to -13.3) | +97 |

The two most accurate forecasters waste slightly *more*, and the two least accurate slightly *less*; the intervals
exclude zero. Across forecasters, expired units track the number of units moved (correlation -0.93 to -0.96 in the
three scenarios), not forecast error. Two mechanisms are consistent with this; neither has been isolated by its own
experiment yet:

1. **Usage is censored and depends on the plan.** A centre's recorded use is what it issued, which is limited by
   what it held. A long-window forecast keeps believing a centre that was short "uses little", so less is sent
   there; a reactive forecast picks up within days that a centre which received stock is now issuing more.
2. **The decision rule is a threshold.** A unit is moved when the forecast crosses stock; a noisier forecast crosses
   more often, so more units are moved, and in this network moving at-risk stock is nearly always better than
   leaving it.

All of these differences are **under 20 expired units in 120 days** (about ±2% in the flat and weekly scenarios, up
to ±6% in the dengue scenario, where totals are smaller), against a reduction of more than 1,100 units from
redistribution itself. Unmet demand changes by under 0.05 percentage points.

## What this means for RaktSetu and for the paper

- The default stays the 14-day moving average: nothing tested changes expired units by more than 20 in 120 days,
  and it is the simplest to explain to blood-bank staff.
- The forecaster is now a pluggable interface (`DemandForecaster`), so a better one can be dropped in.
- **Accuracy is the wrong yardstick for this decision.** Forecasts should be judged by the wastage and shortages
  the resulting plan produces (decision-focused evaluation), and any comparison needs a window-length control.
- The levers that matter more than the forecaster are redistribution itself, supply during surges, and (from the
  digital twin's design) how fresh the stock reports are.

## Threats to validity and next steps

- Synthetic network; illustrative weekday and dengue magnitudes; Poisson demand with no over-dispersion, trend or
  outbreaks beyond the modelled surge. Real demand is burstier, which would favour reactive and intermittent
  methods more than here. **Calibrating against real usage data (for example from e-Raktkosh or a partner
  hospital) is the most valuable next step.**
- One network size and full reporting compliance. Repeating at 50-70% compliance would show whether stale reports
  change the ranking (every forecaster then sees unreported days as zero use).
- Natural extensions: weekday profiles pooled across centres (less noise than per-centre indices), forecasting
  true demand from requests rather than issues to remove censoring, and tuning the risk threshold directly on
  outcomes.
