package com.raktsetu.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FacilityTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    @Test
    void facilityTypesHaveTheDocumentedCapabilities() {
        Facility hub = new HubBloodCentre("HUB01", "Hub");
        Facility storage = new StorageCentre("SC01", "Storage");
        Facility hospital = new Hospital("H01", "Hospital");

        assertThat(hub.canCollect()).isTrue();
        assertThat(hub.canStore()).isTrue();
        assertThat(storage.canCollect()).isFalse();
        assertThat(storage.canStore()).isTrue();
        assertThat(hospital.canStore()).isFalse();
    }

    @Test
    void hospitalsHaveNoInventory() {
        assertThatThrownBy(() -> new Hospital("H01", "Hospital").getInventory())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void eachFacilityOwnsItsOwnInventory() {
        Facility a = new HubBloodCentre("HUB01", "Hub");
        Facility b = new StorageCentre("SC01", "Storage");
        a.getInventory().add(new Platelets("T1", BloodGroup.A_POS, TODAY));
        assertThat(a.getInventory().totalUnits()).isEqualTo(1);
        assertThat(b.getInventory().totalUnits()).isZero();
    }

    @Test
    void tracksDaysSinceLastReport() {
        Facility f = new StorageCentre("SC01", "Storage");
        assertThat(f.daysSinceReport(TODAY)).isEqualTo(Long.MAX_VALUE);
        f.markReported(TODAY.minusDays(3));
        assertThat(f.daysSinceReport(TODAY)).isEqualTo(3);
    }

    @Test
    void facilitiesAreEqualByIdOnly() {
        assertThat(new StorageCentre("SC01", "A")).isEqualTo(new StorageCentre("SC01", "B"));
    }
}
