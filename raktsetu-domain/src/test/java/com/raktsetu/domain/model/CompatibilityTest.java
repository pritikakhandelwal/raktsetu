package com.raktsetu.domain.model;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks every donor/recipient/component combination (8 x 8 x 4) against reference tables written out by hand,
 * independently of the implementation.
 */
class CompatibilityTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    /** Order used by the reference tables below. */
    private static final BloodGroup[] ORDER = {
            BloodGroup.O_NEG, BloodGroup.O_POS, BloodGroup.A_NEG, BloodGroup.A_POS,
            BloodGroup.B_NEG, BloodGroup.B_POS, BloodGroup.AB_NEG, BloodGroup.AB_POS};

    /** Red cells: row = donor, column = recipient, 1 = allowed. */
    private static final String[] RED_CELLS = {
            //         O- O+ A- A+ B- B+ AB- AB+
            "11111111", // O-  universal red-cell donor
            "01010101", // O+
            "00110011", // A-
            "00010001", // A+
            "00001111", // B-
            "00000101", // B+
            "00000011", // AB-
            "00000001", // AB+
    };

    /** Plasma (ABO only, RhD ignored): row = donor, column = recipient, 1 = allowed. */
    private static final String[] PLASMA = {
            "11000000", // O-  O plasma only to O
            "11000000", // O+
            "11110000", // A-  A plasma to O and A
            "11110000", // A+
            "11001100", // B-  B plasma to O and B
            "11001100", // B+
            "11111111", // AB- universal plasma donor
            "11111111", // AB+
    };

    private static boolean expected(String[] table, BloodGroup donor, BloodGroup recipient) {
        return table[indexOf(donor)].charAt(indexOf(recipient)) == '1';
    }

    private static int indexOf(BloodGroup g) {
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i] == g) {
                return i;
            }
        }
        throw new AssertionError(g);
    }

    private static BloodGroup withRh(BloodGroup g, boolean positive) {
        for (BloodGroup candidate : BloodGroup.values()) {
            if (candidate.abo() == g.abo() && candidate.isRhPositive() == positive) {
                return candidate;
            }
        }
        throw new AssertionError(g);
    }

    @Property
    void redCellsMatchReferenceTable(@ForAll BloodGroup donor, @ForAll BloodGroup recipient) {
        BloodComponent unit = new RedCellConcentrate("R1", donor, DAY);
        assertThat(unit.canTransfuseTo(recipient)).isEqualTo(expected(RED_CELLS, donor, recipient));
        assertThat(donor.canGiveRedCellsTo(recipient)).isEqualTo(expected(RED_CELLS, donor, recipient));
    }

    @Property
    void plasmaMatchesReferenceTable(@ForAll BloodGroup donor, @ForAll BloodGroup recipient) {
        BloodComponent unit = new FreshFrozenPlasma("P1", donor, DAY);
        assertThat(unit.canTransfuseTo(recipient)).isEqualTo(expected(PLASMA, donor, recipient));
    }

    @Property
    void plasmaAboRuleIsTheReverseOfRedCells(@ForAll BloodGroup donor, @ForAll BloodGroup recipient) {
        // Remove the RhD effect so only ABO is compared: a negative giver to a positive receiver never fails on RhD.
        boolean reversedRedCellRule = withRh(recipient, false).canGiveRedCellsTo(withRh(donor, true));
        assertThat(donor.canGivePlasmaTo(recipient)).isEqualTo(reversedRedCellRule);
    }

    @Property
    void wholeBloodRequiresAnIdenticalGroup(@ForAll BloodGroup donor, @ForAll BloodGroup recipient) {
        CompatibilityResult r = new WholeBlood("W1", donor, DAY).compatibilityWith(recipient);
        assertThat(r.isAllowed()).isEqualTo(donor == recipient);
    }

    @Property
    void plateletsAreNeverRefusedButNonIdenticalOnesAreFlagged(@ForAll BloodGroup donor,
                                                              @ForAll BloodGroup recipient) {
        CompatibilityResult r = new Platelets("T1", donor, DAY).compatibilityWith(recipient);
        assertThat(r.isAllowed()).isTrue();
        boolean flagged = r.level() == Compatibility.ALLOWED_WITH_WARNING;
        boolean shouldFlag = !donor.sameAboAs(recipient) || (donor.isRhPositive() && !recipient.isRhPositive());
        assertThat(flagged).isEqualTo(shouldFlag);
    }

    @Property
    void identicalGroupIsAlwaysIdentical(@ForAll BloodGroup group, @ForAll ComponentType type) {
        CompatibilityResult r = type.newUnit("U1", group, DAY).compatibilityWith(group);
        assertThat(r.level()).isEqualTo(Compatibility.IDENTICAL);
    }

    @Property
    void everyResultHasAReason(@ForAll BloodGroup donor, @ForAll BloodGroup recipient, @ForAll ComponentType type) {
        assertThat(type.newUnit("U1", donor, DAY).compatibilityWith(recipient).reason()).isNotBlank();
    }

    @Test
    void oNegativeRedCellsToAbPositiveAreAllowed() {
        assertThat(new RedCellConcentrate("R1", BloodGroup.O_NEG, DAY).canTransfuseTo(BloodGroup.AB_POS)).isTrue();
    }

    @Test
    void abPositiveRedCellsToONegativeAreRefusedWithBothReasons() {
        CompatibilityResult r = new RedCellConcentrate("R1", BloodGroup.AB_POS, DAY)
                .compatibilityWith(BloodGroup.O_NEG);
        assertThat(r.level()).isEqualTo(Compatibility.INCOMPATIBLE);
        assertThat(r.reason()).contains("RhD");
    }

    @Test
    void abPlasmaGoesToEveryGroup() {
        for (BloodGroup recipient : BloodGroup.values()) {
            assertThat(new FreshFrozenPlasma("P1", BloodGroup.AB_NEG, DAY).canTransfuseTo(recipient)).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(BloodGroup.class)
    void labelsRoundTripThroughParse(BloodGroup g) {
        assertThat(BloodGroup.parse(g.label())).isEqualTo(g);
        assertThat(BloodGroup.parse(g.name())).isEqualTo(g);
        assertThat(BloodGroup.parse(" " + g.label().toLowerCase() + " ")).isEqualTo(g);
    }

    @Test
    void parseRejectsNonsense() {
        assertThatThrownBy(() -> BloodGroup.parse("C+")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComponentType.parse("serum")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ComponentType.parse("red cells")).isEqualTo(ComponentType.RED_CELLS);
    }
}
