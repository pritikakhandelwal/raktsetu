package com.raktsetu.domain.model;

import java.time.LocalDate;

/** Packed red cells in SAGM additive solution. */
public final class RedCellConcentrate extends BloodComponent {

    public static final int SHELF_LIFE_DAYS = 42;

    public RedCellConcentrate(String unitId, BloodGroup group, LocalDate collectedOn) {
        super(unitId, group, collectedOn);
    }

    public RedCellConcentrate(String unitId, BloodGroup group, LocalDate collectedOn, LocalDate expiryOverride) {
        super(unitId, group, collectedOn, expiryOverride);
    }

    @Override
    public ComponentType getType() {
        return ComponentType.RED_CELLS;
    }

    @Override
    public int getShelfLifeDays() {
        return SHELF_LIFE_DAYS;
    }

    @Override
    public String getStorageCondition() {
        return "2 to 6 °C (SAGM)";
    }

    @Override
    public CompatibilityResult compatibilityWith(BloodGroup recipient) {
        BloodGroup donor = getBloodGroup();
        if (donor == recipient) {
            return CompatibilityResult.of(Compatibility.IDENTICAL, "Red cells " + donor + " match the recipient");
        }
        if (donor.canGiveRedCellsTo(recipient)) {
            return CompatibilityResult.of(Compatibility.COMPATIBLE,
                    donor + " red cells are compatible with a " + recipient + " recipient");
        }
        if (donor.isRhPositive() && !recipient.isRhPositive()) {
            return CompatibilityResult.of(Compatibility.INCOMPATIBLE,
                    "RhD-positive red cells (" + donor + ") must not be given to an RhD-negative recipient ("
                            + recipient + ")");
        }
        return CompatibilityResult.of(Compatibility.INCOMPATIBLE,
                donor + " red cells carry ABO antigens that a " + recipient + " recipient's plasma attacks");
    }
}
