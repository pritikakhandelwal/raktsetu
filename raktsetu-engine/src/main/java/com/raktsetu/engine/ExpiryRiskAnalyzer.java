package com.raktsetu.engine;

import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.UsageHistory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.stream.Collectors;

/**
 * Finds units likely to expire unused.
 *
 * <p>Rule: sort a facility's units of one component and group by expiry. With average daily usage {@code u}, the
 * {@code k}-th unit (1-based) is at risk if {@code k > u * daysToExpiry(k)}: the facility cannot use {@code k} units
 * before that one expires. Only units expiring within the look-ahead window are reported, so long-lived plasma at a
 * quiet centre is not flagged months in advance.
 */
public final class ExpiryRiskAnalyzer {

    public static final int DEFAULT_LOOKAHEAD_DAYS = 7;

    private final int lookaheadDays;
    private final int usageWindowDays;

    public ExpiryRiskAnalyzer() {
        this(DEFAULT_LOOKAHEAD_DAYS, UsageHistory.DEFAULT_WINDOW_DAYS);
    }

    public ExpiryRiskAnalyzer(int lookaheadDays, int usageWindowDays) {
        if (lookaheadDays < 1 || usageWindowDays < 1) {
            throw new IllegalArgumentException("Look-ahead and usage window must be at least one day");
        }
        this.lookaheadDays = lookaheadDays;
        this.usageWindowDays = usageWindowDays;
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
                    double avg = network.usage().averageDaily(f.getId(), type, group, today, usageWindowDays);
                    List<BloodComponent> atRisk = atRisk(usable, avg, today);
                    if (!atRisk.isEmpty()) {
                        items.add(new RiskItem(f, type, group, atRisk, usable.size(), avg, today));
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
        List<BloodComponent> result = new ArrayList<>();
        int k = 0;
        for (BloodComponent unit : fefo) {
            k++;
            long daysLeft = unit.daysToExpiry(today);
            if (daysLeft > lookaheadDays) {
                break;
            }
            if (k > averageDaily * daysLeft) {
                result.add(unit);
            }
        }
        return result;
    }
}
