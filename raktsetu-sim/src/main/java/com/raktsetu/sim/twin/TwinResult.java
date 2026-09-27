package com.raktsetu.sim.twin;

import com.raktsetu.domain.interfaces.Exportable;
import com.raktsetu.domain.model.ComponentType;

import java.util.List;
import java.util.Locale;

/**
 * Measurements from one run, counted only after the warm-up.
 *
 * @param policy              policy name
 * @param compliance          reporting compliance used
 * @param replication         replication number
 * @param days                days measured
 * @param collected           units added to stock, by component, in {@link ComponentType} order
 * @param unitsIssued         units given to hospitals
 * @param expired             expired units by component, in {@link ComponentType} order
 * @param requests            patient requests raised
 * @param requestsUnmet       requests that could not be met in full
 * @param unitsRequested      units asked for
 * @param unitsUnmet          units in unmet requests
 * @param unitsSubstituted    units issued that were not the patient's own group
 * @param oNegativeToOthers   O- units given to patients who are not O-
 * @param transferUnitsPlanned   units the policy proposed to move
 * @param transferUnitsMoved     units actually moved (the rest were already gone or expired: stale data)
 * @param transferKm          road kilometres of executed transfers (one trip each)
 * @param urbanUnitsRequested units requested by urban hospitals
 * @param urbanUnitsUnmet     of which unmet
 * @param ruralUnitsRequested units requested by rural hospitals
 * @param ruralUnitsUnmet     of which unmet
 */
public record TwinResult(String policy, double compliance, int replication, int days, List<Integer> collected,
                         int unitsIssued, List<Integer> expired, int requests, int requestsUnmet, int unitsRequested,
                         int unitsUnmet, int unitsSubstituted, int oNegativeToOthers, int transferUnitsPlanned,
                         int transferUnitsMoved, int transferKm, int urbanUnitsRequested, int urbanUnitsUnmet,
                         int ruralUnitsRequested, int ruralUnitsUnmet) implements Exportable {

    public static final List<String> CSV_HEADER = List.of("policy", "compliance", "replication", "days",
            "units_collected", "units_issued", "units_expired", "expired_whole_blood", "expired_red_cells",
            "expired_platelets", "expired_plasma", "outdate_rate", "outdate_whole_blood", "outdate_red_cells",
            "outdate_platelets", "outdate_plasma", "requests", "requests_unmet", "units_requested",
            "units_unmet", "fill_rate", "units_substituted", "o_negative_to_others", "transfer_units_planned",
            "transfer_units_moved", "transfer_km", "urban_fill_rate", "rural_fill_rate", "rural_urban_gap");

    public TwinResult {
        expired = List.copyOf(expired);
        collected = List.copyOf(collected);
        if (expired.size() != ComponentType.values().length || collected.size() != ComponentType.values().length) {
            throw new IllegalArgumentException("Need one count per component");
        }
    }

    public int unitsCollected() {
        return collected.stream().mapToInt(Integer::intValue).sum();
    }

    public int collected(ComponentType type) {
        return collected.get(type.ordinal());
    }

    /** Share of collected units of one component that expired. */
    public double outdateRate(ComponentType type) {
        return ratio(expired(type), collected(type));
    }

    public int unitsExpired() {
        return expired.stream().mapToInt(Integer::intValue).sum();
    }

    public int expired(ComponentType type) {
        return expired.get(type.ordinal());
    }

    /** Share of collected units that expired: the main wastage measure. */
    public double outdateRate() {
        return ratio(unitsExpired(), unitsCollected());
    }

    /** Share of requested units that were supplied. */
    public double fillRate() {
        return 1 - ratio(unitsUnmet, unitsRequested);
    }

    public double urbanFillRate() {
        return 1 - ratio(urbanUnitsUnmet, urbanUnitsRequested);
    }

    public double ruralFillRate() {
        return 1 - ratio(ruralUnitsUnmet, ruralUnitsRequested);
    }

    /** Urban minus rural fill rate: positive means rural hospitals are served worse. */
    public double ruralUrbanGap() {
        return urbanFillRate() - ruralFillRate();
    }

    private static double ratio(int part, int whole) {
        return whole == 0 ? 0.0 : (double) part / whole;
    }

    @Override
    public List<String> toCsvRow() {
        return List.of(policy, fmt(compliance), String.valueOf(replication), String.valueOf(days),
                String.valueOf(unitsCollected()), String.valueOf(unitsIssued), String.valueOf(unitsExpired()),
                String.valueOf(expired(ComponentType.WHOLE_BLOOD)), String.valueOf(expired(ComponentType.RED_CELLS)),
                String.valueOf(expired(ComponentType.PLATELETS)), String.valueOf(expired(ComponentType.PLASMA)),
                fmt(outdateRate()), fmt(outdateRate(ComponentType.WHOLE_BLOOD)), fmt(outdateRate(ComponentType.RED_CELLS)),
                fmt(outdateRate(ComponentType.PLATELETS)), fmt(outdateRate(ComponentType.PLASMA)), String.valueOf(requests), String.valueOf(requestsUnmet),
                String.valueOf(unitsRequested), String.valueOf(unitsUnmet), fmt(fillRate()),
                String.valueOf(unitsSubstituted), String.valueOf(oNegativeToOthers),
                String.valueOf(transferUnitsPlanned), String.valueOf(transferUnitsMoved), String.valueOf(transferKm),
                fmt(urbanFillRate()), fmt(ruralFillRate()), fmt(ruralUrbanGap()));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.6f", v);
    }
}
