package com.raktsetu.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class NetworkTypesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    @Test
    void usageAverageCoversTheWindowBeforeToday() {
        UsageHistory usage = new UsageHistory();
        usage.record(TODAY.minusDays(1), "SC01", ComponentType.PLATELETS, BloodGroup.B_POS, 4);
        usage.record(TODAY.minusDays(1), "SC01", ComponentType.PLATELETS, BloodGroup.B_POS, 2);
        usage.record(TODAY.minusDays(14), "SC01", ComponentType.PLATELETS, BloodGroup.B_POS, 8);
        usage.record(TODAY.minusDays(15), "SC01", ComponentType.PLATELETS, BloodGroup.B_POS, 100); // outside
        usage.record(TODAY, "SC01", ComponentType.PLATELETS, BloodGroup.B_POS, 100); // today not complete

        assertThat(usage.averageDaily("SC01", ComponentType.PLATELETS, BloodGroup.B_POS, TODAY))
                .isCloseTo(14.0 / 14, within(1e-9));
        assertThat(usage.averageDaily("SC02", ComponentType.PLATELETS, BloodGroup.B_POS, TODAY)).isZero();
        assertThat(usage.entries()).hasSize(4);
    }

    @Test
    void distanceTableIsSymmetricUnlessBothDirectionsAreGiven() {
        TableDistanceProvider table = new TableDistanceProvider(List.of(
                new RouteEntry("A", "B", new Route(10, 20)),
                new RouteEntry("A", "C", new Route(30, 40)),
                new RouteEntry("C", "A", new Route(35, 50))));

        assertThat(table.route("B", "A")).contains(new Route(10, 20));
        assertThat(table.route("C", "A")).contains(new Route(35, 50));
        assertThat(table.route("A", "A")).contains(Route.ZERO);
        assertThat(table.route("B", "C")).isEmpty();
    }

    @Test
    void transferOrderCanOnlyLeaveProposedOnce() {
        Facility a = new StorageCentre("SC01", "A");
        Facility b = new StorageCentre("SC02", "B");
        TransferOrder order = new TransferOrder("T1", a, b,
                List.of(new Platelets("RS-000001", BloodGroup.B_POS, TODAY)), TODAY, new Route(5, 10), "test");

        assertThat(order.describeUnits()).isEqualTo("1 x PLATELETS B+");
        order.markCompleted();
        assertThat(order.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThatThrownBy(order::reject).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void transferOrderNeedsTwoFacilitiesAndUnits() {
        Facility a = new StorageCentre("SC01", "A");
        assertThatThrownBy(() -> new TransferOrder("T1", a, a,
                List.of(new Platelets("U", BloodGroup.B_POS, TODAY)), TODAY, Route.ZERO, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransferOrder("T1", a, new StorageCentre("SC02", "B"), List.of(), TODAY,
                Route.ZERO, "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unitIdsRoundTrip() {
        assertThat(UnitIds.format(123)).isEqualTo("RS-000123");
        assertThat(UnitIds.serialOf("RS-000123")).isEqualTo(123);
        assertThat(UnitIds.serialOf("W1234 26 000123")).isZero();
    }

    @Test
    void requestsNeedAtLeastOneUnit() {
        assertThatThrownBy(() -> new BloodRequest("H01", ComponentType.RED_CELLS, BloodGroup.O_POS, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new BloodRequest("H01", ComponentType.RED_CELLS, BloodGroup.O_POS, 2).urgency())
                .isEqualTo(Urgency.EMERGENCY);
    }
}
