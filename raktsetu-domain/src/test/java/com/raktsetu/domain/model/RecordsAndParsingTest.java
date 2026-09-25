package com.raktsetu.domain.model;

import com.raktsetu.domain.exception.FacilityNotFoundException;
import com.raktsetu.domain.exception.IncompatibleBloodException;
import com.raktsetu.domain.exception.InsufficientStockException;
import com.raktsetu.domain.exception.InvalidDataException;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Value types, factories, parsing and exception messages. */
class RecordsAndParsingTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    @Property
    void facilityFactoryCreatesTheMatchingSubclass(@ForAll FacilityType type) {
        Facility f = type.newFacility("F1", "Name", DAY);
        assertThat(f.getFacilityType()).isEqualTo(type);
        assertThat(FacilityType.parse(" " + type.name().toLowerCase() + " ")).isEqualTo(type);
        assertThat(f.canStore()).isEqualTo(type != FacilityType.HOSPITAL);
        assertThat(f.canCollect()).isEqualTo(type == FacilityType.HUB);
    }

    @Property
    void unitFactoryWithExplicitExpiryKeepsTheDate(@ForAll ComponentType type) {
        BloodComponent unit = type.newUnit("U1", BloodGroup.A_POS, DAY, DAY.plusDays(2));
        assertThat(unit.getType()).isEqualTo(type);
        assertThat(unit.getExpiryDate()).isEqualTo(DAY.plusDays(2));
        assertThat(unit.getStorageCondition()).isNotBlank();
        assertThat(unit.toString()).contains("U1").contains("A+");
    }

    @Test
    void parsersRejectUnknownValues() {
        assertThatThrownBy(() -> FacilityType.parse("clinic")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FacilityType.parse(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Urgency.parse("soon")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Urgency.parse(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComponentType.parse(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BloodGroup.parse(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Urgency.parse("routine")).isEqualTo(Urgency.ROUTINE);
    }

    @Test
    void auditEventNormalisesOptionalFieldsAndNeedsUnits() {
        AuditEvent e = new AuditEvent(DAY, AuditEventType.DISCARDED, "SC01", null, ComponentType.PLATELETS,
                BloodGroup.O_POS, List.of("U1"), null);
        assertThat(e.counterparty()).isEmpty();
        assertThat(e.note()).isEmpty();
        assertThatThrownBy(() -> new AuditEvent(DAY, AuditEventType.ISSUED, "SC01", "H01", ComponentType.PLATELETS,
                BloodGroup.O_POS, List.of(), "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wastageRecordExportsOneRow() {
        WastageRecord w = WastageRecord.of(DAY, "SC01", new Platelets("U1", BloodGroup.B_NEG, DAY.minusDays(5)));
        assertThat(w.toCsvRow()).containsExactly("2026-10-01", "SC01", "U1", "PLATELETS", "B-");
    }

    @Test
    void networkSnapshotValidatesAndCopies() {
        NetworkData data = new NetworkData(List.of(new StorageCentre("SC01", "A")), List.of(), new UsageHistory(),
                List.of(), 1);
        assertThat(new LoadResult(data, List.of("problem")).problems()).containsExactly("problem");
        assertThatThrownBy(() -> new NetworkData(List.of(), List.of(), new UsageHistory(), List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Route(-1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transferOrderExportsAndDescribesMixedUnits() {
        TransferOrder order = new TransferOrder("T1", new StorageCentre("SC01", "A"), new StorageCentre("SC02", "B"),
                List.of(new Platelets("U1", BloodGroup.B_POS, DAY), new Platelets("U2", BloodGroup.O_POS, DAY)),
                DAY, new Route(12, 30), "why");
        assertThat(order.describeUnits()).isEqualTo("2 units");
        assertThat(order.toCsvRow()).containsExactly("T1", "2026-10-01", "SC01", "SC02", "2 units", "12", "30",
                "PROPOSED", "why");
        assertThat(order.toString()).contains("SC01 -> SC02");
        order.reject();
        assertThat(order.getStatus()).isEqualTo(TransferStatus.REJECTED);
    }

    @Test
    void exceptionMessagesAreReadable() {
        assertThat(new FacilityNotFoundException("X9")).hasMessage("No facility with ID 'X9'");
        assertThat(new IncompatibleBloodException("reason")).hasMessageContaining("reason");
        assertThat(new InvalidDataException("units.csv", 4, "bad")).hasMessage("units.csv, line 4: bad");
        InsufficientStockException e = new InsufficientStockException("O- RED_CELLS", 3, 1, "SC01: 1");
        assertThat(e).hasMessage("Not enough O- RED_CELLS: requested 3, available 1 (SC01: 1)");
        assertThat(e.getRequested()).isEqualTo(3);
        assertThat(e.getAvailable()).isEqualTo(1);
    }

    @Test
    void unitIdsRejectBadSerials() {
        assertThatThrownBy(() -> UnitIds.format(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(UnitIds.serialOf(null)).isZero();
        assertThat(UnitIds.serialOf("RS-abc")).isZero();
    }
}
