package com.raktsetu.domain.model;

import java.time.LocalDate;

/**
 * Platelet concentrate. ABO-identical platelets are preferred; other groups are allowed but flagged, and
 * RhD-positive platelets for an RhD-negative recipient are flagged because of the risk of anti-D sensitisation.
 */
public final class Platelets extends BloodComponent {

    public static final int SHELF_LIFE_DAYS = 5;

    public Platelets(String unitId, BloodGroup group, LocalDate collectedOn) {
        super(unitId, group, collectedOn);
    }

    public Platelets(String unitId, BloodGroup group, LocalDate collectedOn, LocalDate expiryOverride) {
        super(unitId, group, collectedOn, expiryOverride);
    }

    @Override
    public ComponentType getType() {
        return ComponentType.PLATELETS;
    }

    @Override
    public int getShelfLifeDays() {
        return SHELF_LIFE_DAYS;
    }

    @Override
    public String getStorageCondition() {
        return "20 to 24 °C with continuous agitation";
    }

    @Override
    public CompatibilityResult compatibilityWith(BloodGroup recipient) {
        BloodGroup donor = getBloodGroup();
        boolean rhRisk = donor.isRhPositive() && !recipient.isRhPositive();
        if (donor == recipient) {
            return CompatibilityResult.of(Compatibility.IDENTICAL, "Platelets " + donor + " match the recipient");
        }
        if (donor.sameAboAs(recipient) && !rhRisk) {
            return CompatibilityResult.of(Compatibility.COMPATIBLE,
                    "ABO-identical platelets; RhD-negative platelets are safe for an RhD-positive recipient");
        }
        StringBuilder reason = new StringBuilder();
        if (!donor.sameAboAs(recipient)) {
            reason.append("ABO-non-identical platelets (").append(donor).append(" for ").append(recipient)
                    .append("); ABO-identical preferred");
        }
        if (rhRisk) {
            if (!reason.isEmpty()) {
                reason.append("; ");
            }
            reason.append("RhD-positive platelets for an RhD-negative recipient: consider anti-D prophylaxis");
        }
        return CompatibilityResult.of(Compatibility.ALLOWED_WITH_WARNING, reason.toString());
    }
}
