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
                    List<BloodComponent> usable = f.getInventory().units(type, group).stream()
                            .filter(u -> !u.isExpired(today)).toList();
                    if (usable.isEmpty()) {
                        continue;
                    }
                    double avg = network.usage().averageDaily(f.getId(), type, group, today, usageWindowDays);
                    List<BloodComponent> atRisk = atRisk(usable, avg, today);
                    if (!atRisk.isEmpty()) {
                        items.add(new RiskItem(f, type, group, atRisk, usable.size(), avg, today));
                    }
                }
            }
        }
        items.sort(Comparator.comparingLong(RiskItem::soonestDaysLeft)
                .thenComparing(Comparator.comparingInt((RiskItem r) -> r.atRisk().size()).reversed())
                .thenComparing(r -> r.facility().getId()));
        return items;
    }

    /** Applies the rule to units already in first-expiry-first-out order. */
    List<BloodComponent> atRisk(List<BloodComponent> fefo, double averageDaily, LocalDate today) {
        List<BloodComponent> result = new ArrayList<>();
        for (int k = 1; k <= fefo.size(); k++) {
            BloodComponent unit = fefo.get(k - 1);
            long daysLeft = unit.daysToExpiry(today);
            if (daysLeft <= lookaheadDays && k > averageDaily * daysLeft) {
                result.add(unit);
            }
        }
        return result;
    }
}
