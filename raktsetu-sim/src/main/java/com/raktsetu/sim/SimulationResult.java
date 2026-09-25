package com.raktsetu.sim;

import com.raktsetu.domain.interfaces.Exportable;
import com.raktsetu.domain.model.ComponentType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Totals from one simulation run.
 *
 * @param policy             "no redistribution" or the strategy name
 * @param days               days simulated
 * @param unitsCollected     units supplied to the network
 * @param expiredByComponent units that expired, per component
 * @param requestsFulfilled  requests met in full
 * @param requestsUnmet      requests that could not be met
 * @param unitsIssued        units given to hospitals
 * @param unitsTransferred   units moved by approved transfers
 */
public record SimulationResult(String policy, int days, int unitsCollected,
                               Map<ComponentType, Integer> expiredByComponent, int requestsFulfilled,
                               int requestsUnmet, int unitsIssued, int unitsTransferred) implements Exportable {

    public static final List<String> CSV_HEADER = List.of("policy", "days", "units_collected", "units_expired",
            "expired_whole_blood", "expired_red_cells", "expired_platelets", "expired_plasma", "requests_fulfilled",
            "requests_unmet", "units_issued", "units_transferred");

    public SimulationResult {
        expiredByComponent = Map.copyOf(new EnumMap<>(expiredByComponent));
    }

    public int unitsExpired() {
        return expiredByComponent.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int expired(ComponentType type) {
        return expiredByComponent.getOrDefault(type, 0);
    }

    /** Percentage reduction in expired units compared with a baseline run. */
    public double wastageReductionVersus(SimulationResult baseline) {
        int base = baseline.unitsExpired();
        return base == 0 ? 0.0 : 100.0 * (base - unitsExpired()) / base;
    }

    @Override
    public List<String> toCsvRow() {
        return List.of(policy, String.valueOf(days), String.valueOf(unitsCollected), String.valueOf(unitsExpired()),
                String.valueOf(expired(ComponentType.WHOLE_BLOOD)), String.valueOf(expired(ComponentType.RED_CELLS)),
                String.valueOf(expired(ComponentType.PLATELETS)), String.valueOf(expired(ComponentType.PLASMA)),
                String.valueOf(requestsFulfilled), String.valueOf(requestsUnmet), String.valueOf(unitsIssued),
                String.valueOf(unitsTransferred));
    }
}
