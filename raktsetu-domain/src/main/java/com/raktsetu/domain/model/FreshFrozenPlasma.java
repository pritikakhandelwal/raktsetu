package com.raktsetu.domain.model;

import java.time.LocalDate;

/** Fresh frozen plasma. Its ABO rule is the reverse of red cells: AB is the universal plasma donor. */
public final class FreshFrozenPlasma extends BloodComponent {

    public static final int SHELF_LIFE_DAYS = 365;

    public FreshFrozenPlasma(String unitId, BloodGroup group, LocalDate collectedOn) {
        super(unitId, group, collectedOn);
    }

    public FreshFrozenPlasma(String unitId, BloodGroup group, LocalDate collectedOn, LocalDate expiryOverride) {
        super(unitId, group, collectedOn, expiryOverride);
    }

    @Override
    public ComponentType getType() {
        return ComponentType.PLASMA;
    }

    @Override
    public int getShelfLifeDays() {
        return SHELF_LIFE_DAYS;
    }

    @Override
    public String getStorageCondition() {
        return "-30 °C or colder; use within 6 hours of thawing";
    }

    @Override
    public CompatibilityResult compatibilityWith(BloodGroup recipient) {
        BloodGroup donor = getBloodGroup();
        if (donor.sameAboAs(recipient)) {
            return CompatibilityResult.of(Compatibility.IDENTICAL,
                    "Plasma " + donor.abo() + " matches the recipient's ABO group (RhD does not matter for plasma)");
        }
        if (donor.canGivePlasmaTo(recipient)) {
            return CompatibilityResult.of(Compatibility.COMPATIBLE,
                    donor.abo() + " plasma has no antibodies against a " + recipient.abo()
                            + " recipient (plasma rules are the reverse of red cells)");
        }
        return CompatibilityResult.of(Compatibility.INCOMPATIBLE,
                donor.abo() + " plasma carries antibodies against a " + recipient.abo()
                        + " recipient's red cells (plasma rules are the reverse of red cells)");
    }
}
