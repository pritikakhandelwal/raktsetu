package com.raktsetu.domain.model;

import java.time.LocalDate;
import java.util.Locale;

/** The four blood components RaktSetu tracks. */
public enum ComponentType {
    WHOLE_BLOOD,
    RED_CELLS,
    PLATELETS,
    PLASMA;

    /** Factory used by loaders and the console so callers never need to know the concrete subclasses. */
    public BloodComponent newUnit(String unitId, BloodGroup group, LocalDate collectedOn) {
        return switch (this) {
            case WHOLE_BLOOD -> new WholeBlood(unitId, group, collectedOn);
            case RED_CELLS -> new RedCellConcentrate(unitId, group, collectedOn);
            case PLATELETS -> new Platelets(unitId, group, collectedOn);
            case PLASMA -> new FreshFrozenPlasma(unitId, group, collectedOn);
        };
    }

    /**
     * Parses names such as {@code RED_CELLS}, {@code red cells} or {@code platelets}.
     *
     * @throws IllegalArgumentException if the text is not a component type
     */
    public static ComponentType parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Component type is missing");
        }
        String t = text.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return valueOf(t);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown component: '" + text + "' (expected WHOLE_BLOOD, RED_CELLS, PLATELETS or PLASMA)", e);
        }
    }
}
