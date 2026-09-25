package com.raktsetu.domain.model;

import java.time.LocalDate;

/** Whole blood contains both red cells and plasma, so it must match the recipient's group exactly. */
public final class WholeBlood extends BloodComponent {

    public static final int SHELF_LIFE_DAYS = 35;

    public WholeBlood(String unitId, BloodGroup group, LocalDate collectedOn) {
        super(unitId, group, collectedOn);
    }

    public WholeBlood(String unitId, BloodGroup group, LocalDate collectedOn, LocalDate expiryOverride) {
        super(unitId, group, collectedOn, expiryOverride);
    }

    @Override
    public ComponentType getType() {
        return ComponentType.WHOLE_BLOOD;
    }

    @Override
    public int getShelfLifeDays() {
        return SHELF_LIFE_DAYS;
    }

    @Override
    public String getStorageCondition() {
        return "2 to 6 °C (CPDA-1)";
    }

    @Override
    public CompatibilityResult compatibilityWith(BloodGroup recipient) {
        BloodGroup donor = getBloodGroup();
        if (donor == recipient) {
            return CompatibilityResult.of(Compatibility.IDENTICAL, "Whole blood " + donor + " matches the recipient");
        }
        return CompatibilityResult.of(Compatibility.INCOMPATIBLE,
                "Whole blood contains cells and plasma, so it must be group-identical; " + donor + " is not "
                        + recipient);
    }
}
