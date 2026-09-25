package com.raktsetu.engine.strategy;

import com.raktsetu.domain.interfaces.Exportable;
import com.raktsetu.domain.model.TransferOrder;

import java.util.List;

/**
 * One strategy's plan, summarised for side-by-side comparison.
 *
 * @param strategy    strategy name
 * @param orders      the proposed transfers
 * @param atRiskUnits at-risk units in the network before the plan
 */
public record StrategyComparison(String strategy, List<TransferOrder> orders, int atRiskUnits) implements Exportable {

    public StrategyComparison {
        orders = List.copyOf(orders);
    }

    /** Units the plan moves to a centre expected to use them. */
    public int unitsRescued() {
        return orders.stream().mapToInt(TransferOrder::unitCount).sum();
    }

    /** Total road distance, counting one trip per order. */
    public int totalKm() {
        return orders.stream().mapToInt(o -> o.getRoute().distanceKm()).sum();
    }

    public int stillAtRisk() {
        return atRiskUnits - unitsRescued();
    }

    @Override
    public List<String> toCsvRow() {
        return List.of(strategy, String.valueOf(orders.size()), String.valueOf(unitsRescued()),
                String.valueOf(stillAtRisk()), String.valueOf(totalKm()));
    }
}
