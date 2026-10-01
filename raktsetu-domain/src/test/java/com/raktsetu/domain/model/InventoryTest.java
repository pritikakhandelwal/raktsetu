package com.raktsetu.domain.model;

import com.raktsetu.domain.exception.InsufficientStockException;
import com.raktsetu.domain.exception.UnitExpiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.raktsetu.domain.model.BloodGroup.A_POS;
import static com.raktsetu.domain.model.BloodGroup.O_NEG;
import static com.raktsetu.domain.model.ComponentType.PLATELETS;
import static com.raktsetu.domain.model.ComponentType.RED_CELLS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    private Inventory inventory;

    @BeforeEach
    void setUp() {
        inventory = new HubBloodCentre("HUB01", "Hub").getInventory();
    }

    private void addRedCells(String id, int daysAgo) {
        inventory.add(new RedCellConcentrate(id, O_NEG, TODAY.minusDays(daysAgo)));
    }

    @Test
    void issuesSoonestExpiringUnitsFirst() throws Exception {
        addRedCells("NEW", 1);
        addRedCells("OLDEST", 30);
        addRedCells("MIDDLE", 10);

        List<BloodComponent> issued = inventory.issue(RED_CELLS, O_NEG, 2, TODAY);

        assertThat(issued).extracting(BloodComponent::getId).containsExactly("OLDEST", "MIDDLE");
        assertThat(inventory.count(RED_CELLS, O_NEG)).isEqualTo(1);
    }

    @Test
    void insufficientStockIssuesNothing() {
        addRedCells("R1", 1);
        addRedCells("R2", 2);

        assertThatThrownBy(() -> inventory.issue(RED_CELLS, O_NEG, 3, TODAY))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("requested 3, available 2");
        assertThat(inventory.count(RED_CELLS, O_NEG)).isEqualTo(2);
    }

    @Test
    void neverIssuesAnExpiredUnit() {
        inventory.add(new Platelets("OLD", A_POS, TODAY.minusDays(6)));
        inventory.add(new Platelets("FRESH", A_POS, TODAY.minusDays(1)));

        assertThatThrownBy(() -> inventory.issue(PLATELETS, A_POS, 1, TODAY))
                .isInstanceOf(UnitExpiredException.class);
        assertThat(inventory.count(PLATELETS, A_POS)).isEqualTo(2);
    }

    @Test
    void removeExpiredReturnsWastageAndUnblocksIssuing() throws Exception {
        inventory.add(new Platelets("OLD", A_POS, TODAY.minusDays(6)));
        inventory.add(new Platelets("EXPIRES_TODAY", A_POS, TODAY.minusDays(5)));
        inventory.add(new Platelets("FRESH", A_POS, TODAY.minusDays(1)));

        assertThat(inventory.removeExpired(TODAY)).extracting(BloodComponent::getId)
                .containsExactly("OLD", "EXPIRES_TODAY");
        assertThat(inventory.issue(PLATELETS, A_POS, 1, TODAY)).extracting(BloodComponent::getId)
                .containsExactly("FRESH");
        assertThat(inventory.totalUnits()).isZero();
    }

    @Test
    void countUsableIgnoresExpiredUnits() {
        inventory.add(new Platelets("OLD", A_POS, TODAY.minusDays(6)));
        inventory.add(new Platelets("FRESH", A_POS, TODAY.minusDays(1)));
        assertThat(inventory.count(PLATELETS, A_POS)).isEqualTo(2);
        assertThat(inventory.countUsable(PLATELETS, A_POS, TODAY)).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateUnitIds() {
        addRedCells("R1", 1);
        assertThatThrownBy(() -> addRedCells("R1", 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void takeRemovesOneSpecificUnit() {
        addRedCells("R1", 1);
        addRedCells("R2", 2);
        assertThat(inventory.take("R1")).isPresent();
        assertThat(inventory.take("R1")).isEmpty();
        assertThat(inventory.units(RED_CELLS, O_NEG)).extracting(BloodComponent::getId).containsExactly("R2");
    }

    @Test
    void expiringWithinListsOnlyUnexpiredUnitsInTheWindow() {
        inventory.add(new Platelets("EXPIRED", A_POS, TODAY.minusDays(5)));
        inventory.add(new Platelets("ONE_DAY_LEFT", A_POS, TODAY.minusDays(4)));
        inventory.add(new Platelets("FOUR_DAYS_LEFT", A_POS, TODAY.minusDays(1)));

        assertThat(inventory.expiringWithin(2, TODAY)).extracting(BloodComponent::getId)
                .containsExactly("ONE_DAY_LEFT");
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> inventory.issue(RED_CELLS, O_NEG, 0, TODAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usableIsALiveViewInExpiryOrderWithoutExpiredUnits() {
        inventory.add(new Platelets("OLD", A_POS, TODAY.minusDays(6)));   // expired
        inventory.add(new Platelets("B", A_POS, TODAY.minusDays(1)));
        inventory.add(new Platelets("A", A_POS, TODAY.minusDays(1)));     // same expiry: ID order
        inventory.add(new Platelets("SOON", A_POS, TODAY.minusDays(4)));

        assertThat(inventory.usable(PLATELETS, A_POS, TODAY)).extracting(BloodComponent::getId)
                .containsExactly("SOON", "A", "B");
        assertThat(inventory.countUsable(PLATELETS, A_POS, TODAY)).isEqualTo(3);
        assertThat(inventory.usable(PLATELETS, A_POS, TODAY.plusDays(10))).isEmpty();
        assertThat(inventory.usable(RED_CELLS, O_NEG, TODAY)).isEmpty();
        assertThatThrownBy(() -> inventory.usable(PLATELETS, A_POS, TODAY).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void removeExpiredSkipsWorkUntilSomethingCanExpireButNeverMissesANewUnit() {
        addRedCells("R1", 10);                                   // expires in 32 days
        assertThat(inventory.removeExpired(TODAY)).isEmpty();
        // Added after the sweep with an earlier expiry: must still be found.
        inventory.add(new Platelets("P1", A_POS, TODAY.minusDays(5)));
        assertThat(inventory.removeExpired(TODAY)).extracting(BloodComponent::getId).containsExactly("P1");
        assertThat(inventory.removeExpired(TODAY)).isEmpty();
        assertThat(inventory.removeExpired(TODAY.plusDays(32))).extracting(BloodComponent::getId)
                .containsExactly("R1");
        assertThat(inventory.totalUnits()).isZero();
        assertThat(inventory.removeExpired(TODAY.plusDays(400))).isEmpty();
    }

    @Test
    void takingAnyUnitKeepsTheOrder() {
        addRedCells("R1", 30);
        addRedCells("R2", 20);
        addRedCells("R3", 10);
        assertThat(inventory.take("R2")).isPresent();
        assertThat(inventory.units(RED_CELLS, O_NEG)).extracting(BloodComponent::getId).containsExactly("R1", "R3");
    }
}
