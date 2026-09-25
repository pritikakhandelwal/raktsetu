package com.raktsetu.domain.model;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpiryTest {

    private static final LocalDate COLLECTED = LocalDate.of(2026, 10, 1);

    @Test
    void plateletsExpireOnDayFiveNotAfter() {
        Platelets p = new Platelets("T1", BloodGroup.B_POS, COLLECTED);
        assertThat(p.getExpiryDate()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(p.isExpired(COLLECTED.plusDays(4))).isFalse();
        assertThat(p.daysToExpiry(COLLECTED.plusDays(4))).isEqualTo(1);
        assertThat(p.isExpired(COLLECTED.plusDays(5))).isTrue();
        assertThat(p.isExpired(COLLECTED.plusDays(6))).isTrue();
    }

    @Test
    void shelfLivesAreThoseDocumentedInTheRoadmap() {
        assertThat(new WholeBlood("W", BloodGroup.O_POS, COLLECTED).getShelfLifeDays()).isEqualTo(35);
        assertThat(new RedCellConcentrate("R", BloodGroup.O_POS, COLLECTED).getShelfLifeDays()).isEqualTo(42);
        assertThat(new Platelets("T", BloodGroup.O_POS, COLLECTED).getShelfLifeDays()).isEqualTo(5);
        assertThat(new FreshFrozenPlasma("P", BloodGroup.O_POS, COLLECTED).getShelfLifeDays()).isEqualTo(365);
    }

    @Test
    void explicitExpiryOverridesShelfLife() {
        LocalDate shortened = COLLECTED.plusDays(14);
        RedCellConcentrate r = new RedCellConcentrate("R1", BloodGroup.A_NEG, COLLECTED, shortened);
        assertThat(r.getExpiryDate()).isEqualTo(shortened);
    }

    @Test
    void expiryCannotBeOnOrBeforeCollection() {
        assertThatThrownBy(() -> new RedCellConcentrate("R1", BloodGroup.A_NEG, COLLECTED, COLLECTED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unitIdIsRequired() {
        assertThatThrownBy(() -> new Platelets(" ", BloodGroup.A_NEG, COLLECTED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Property
    void expiredExactlyWhenNoDaysAreLeft(@ForAll ComponentType type, @ForAll @IntRange(min = 0, max = 400) int age) {
        BloodComponent unit = type.newUnit("U1", BloodGroup.O_NEG, COLLECTED);
        LocalDate today = COLLECTED.plusDays(age);
        assertThat(unit.isExpired(today)).isEqualTo(unit.daysToExpiry(today) <= 0);
    }
}
