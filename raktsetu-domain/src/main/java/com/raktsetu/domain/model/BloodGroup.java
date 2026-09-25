package com.raktsetu.domain.model;

import java.util.Locale;

/**
 * The eight ABO/RhD blood groups.
 *
 * <p>This enum is the single source of truth for the two basic compatibility relations. Component classes decide
 * which relation applies to them (red cells vs plasma) and add their own clinical nuances on top.
 */
public enum BloodGroup {
    O_NEG(Abo.O, false),
    O_POS(Abo.O, true),
    A_NEG(Abo.A, false),
    A_POS(Abo.A, true),
    B_NEG(Abo.B, false),
    B_POS(Abo.B, true),
    AB_NEG(Abo.AB, false),
    AB_POS(Abo.AB, true);

    /** ABO type, described by the red-cell antigens it carries. */
    public enum Abo {
        O(false, false),
        A(true, false),
        B(false, true),
        AB(true, true);

        private final boolean hasA;
        private final boolean hasB;

        Abo(boolean hasA, boolean hasB) {
            this.hasA = hasA;
            this.hasB = hasB;
        }

        public boolean hasAntigenA() {
            return hasA;
        }

        public boolean hasAntigenB() {
            return hasB;
        }

        /** True if every ABO antigen on {@code this} is also present on {@code other}. */
        boolean antigensSubsetOf(Abo other) {
            return (!hasA || other.hasA) && (!hasB || other.hasB);
        }
    }

    private final Abo abo;
    private final boolean rhPositive;

    BloodGroup(Abo abo, boolean rhPositive) {
        this.abo = abo;
        this.rhPositive = rhPositive;
    }

    public Abo abo() {
        return abo;
    }

    public boolean isRhPositive() {
        return rhPositive;
    }

    /**
     * Red-cell rule: the donor's cells must not carry an ABO antigen the recipient lacks (the recipient's plasma would
     * attack it), and an RhD-negative recipient must receive RhD-negative cells.
     */
    public boolean canGiveRedCellsTo(BloodGroup recipient) {
        boolean aboOk = abo.antigensSubsetOf(recipient.abo);
        boolean rhOk = !rhPositive || recipient.rhPositive;
        return aboOk && rhOk;
    }

    /**
     * Plasma rule, the reverse of red cells: the donor's plasma carries antibodies against the ABO antigens the donor
     * lacks, so the recipient must not carry any antigen the donor lacks. RhD does not matter for plasma.
     */
    public boolean canGivePlasmaTo(BloodGroup recipient) {
        return recipient.abo.antigensSubsetOf(abo);
    }

    public boolean sameAboAs(BloodGroup other) {
        return abo == other.abo;
    }

    /** Human-readable label, for example {@code O-} or {@code AB+}. */
    public String label() {
        return abo.name() + (rhPositive ? "+" : "-");
    }

    /**
     * Parses {@code O-}, {@code o+}, {@code AB_POS}, {@code ab pos} and similar forms.
     *
     * @throws IllegalArgumentException if the text is not a blood group
     */
    public static BloodGroup parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Blood group is missing");
        }
        String t = text.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("−", "-");
        for (BloodGroup g : values()) {
            if (t.equals(g.label()) || t.equals(g.name().replace("_", ""))) {
                return g;
            }
        }
        throw new IllegalArgumentException("Unknown blood group: '" + text + "' (expected e.g. O-, A+, AB+)");
    }

    @Override
    public String toString() {
        return label();
    }
}
