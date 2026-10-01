package com.raktsetu.engine;

import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.UsageHistory;

import com.raktsetu.engine.forecast.DemandForecaster;
import com.raktsetu.engine.forecast.DemandForecaster.Forecast;
import com.raktsetu.engine.forecast.MovingAverageForecaster;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.stream.Collectors;

/**
 * Finds units likely to expire unused.
 *
 * <p>Rule: sort a facility's units of one component and group by expiry. With average daily usage {@code u}, the
 * {@code k}-th unit (1-based) is at risk if {@code k > D(daysToExpiry(k))}, where {@code D(n)} is the demand forecast
 * for the next {@code n} days: the facility is not expected to use {@code k} units before that one expires. With the
 * default moving-average forecaster, {@code D(n) = u * n} for the recent daily average {@code u}. Only units expiring within the look-ahead window are reported, so long-lived plasma at a
 * quiet centre is not flagged months in advance.
 */
public final class ExpiryRiskAnalyzer {

    public static final int DEFAULT_LOOKAHEAD_DAYS = 7;

    private final int lookaheadDays;
    private final DemandForecaster forecaster;

    public ExpiryRiskAnalyzer() {
        this(DEFAULT_LOOKAHEAD_DAYS, UsageHistory.DEFAULT_WINDOW_DAYS);
    }

    /** With the moving-average forecaster over {@code usageWindowDays}. */
    public ExpiryRiskAnalyzer(int lookaheadDays, int usageWindowDays) {
        this(lookaheadDays, windowForecaster(usageWindowDays));
    }

    public ExpiryRiskAnalyzer(int lookaheadDays, DemandForecaster forecaster) {
        if (lookaheadDays < 1) {
            throw new IllegalArgumentException("Look-ahead must be at least one day");
        }
        this.lookaheadDays = lookaheadDays;
        this.forecaster = Objects.requireNonNull(forecaster, "forecaster");
    }

    private static DemandForecaster windowForecaster(int usageWindowDays) {
        if (usageWindowDays < 1) {
            throw new IllegalArgumentException("Look-ahead and usage window must be at least one day");
        }
        return new MovingAverageForecaster(usageWindowDays);
    }

    public DemandForecaster forecaster() {
        return forecaster;
    }

    /** All at-risk stock in the network, soonest-expiring first. */
    public List<RiskItem> analyze(BloodNetwork network, LocalDate today) {
        List<RiskItem> items = new ArrayList<>();
        for (Facility f : network.storingFacilities()) {
            for (ComponentType type : ComponentType.values()) {
                for (BloodGroup group : BloodGroup.values()) {
                    SortedSet<BloodComponent> usable = f.getInventory().usable(type, group, today);
                    if (usable.isEmpty() || usable.first().daysToExpiry(today) > lookaheadDays) {
                        continue; // nothing here expires within the look-ahead, so nothing can be at risk
                    }
                    Forecast demand = forecaster.forecast(network.usage(), f.getId(), type, group, today);
                    List<BloodComponent> atRisk = atRisk(usable, demand, today);
                    if (!atRisk.isEmpty()) {
                        items.add(new RiskItem(f, type, group, atRisk, usable.size(), demand.dailyRate(), today));
                    }
                }
            }
        }
        // Sort on keys worked out once per item rather than on every comparison.
        record Keyed(RiskItem item, long soonest) {
        }
        return items.stream().map(r -> new Keyed(r, r.soonestDaysLeft()))
                .sorted(Comparator.comparingLong(Keyed::soonest)
                        .thenComparing(Comparator.comparingInt((Keyed k) -> k.item().atRisk().size()).reversed())
                        .thenComparing(k -> k.item().facility().getId()))
                .map(Keyed::item)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Applies the rule to units already in first-expiry-first-out order. Stops at the first unit beyond the
     * look-ahead: every later unit expires even later, so none of them can be reported.
     */
    List<BloodComponent> atRisk(Iterable<BloodComponent> fefo, double averageDaily, LocalDate today) {
        return atRisk(fefo, new DemandForecaster.Flat(averageDaily), today);
    }

    List<BloodComponent> atRisk(Iterable<BloodComponent> fefo, Forecast demand, LocalDate today) {
        List<BloodComponent> result = new ArrayList<>();
        int k = 0;
        for (BloodComponent unit : fefo) {
            k++;
            long daysLeft = unit.daysToExpiry(today);
            if (daysLeft > lookaheadDays) {
                break;
            }
            if (k > demand.over(daysLeft)) {
                result.add(unit);
            }
        }
        return result;
    }
}
