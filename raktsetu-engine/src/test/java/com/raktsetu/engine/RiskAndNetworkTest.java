package com.raktsetu.engine;

import com.raktsetu.domain.model.AuditEventType;
import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.UnitIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.raktsetu.domain.model.BloodGroup.B_POS;
import static com.raktsetu.domain.model.ComponentType.PLASMA;
import static com.raktsetu.domain.model.ComponentType.PLATELETS;
import static com.raktsetu.engine.TestNetwork.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskAndNetworkTest {

    private TestNetwork t;
    private final ExpiryRiskAnalyzer analyzer = new ExpiryRiskAnalyzer();

    @BeforeEach
    void setUp() {
        t = new TestNetwork();
    }

    @Test
    void flagsExactlyTheUnitsThatCannotBeUsedInTime() {
        t.add(t.sc01, PLATELETS, B_POS, 3, 12); // 2 days left each
        t.usagePerDay(t.sc01, PLATELETS, B_POS, 3); // can use 6 in 2 days

        List<RiskItem> risks = analyzer.analyze(t.network, TODAY);

        assertThat(risks).singleElement().satisfies(r -> {
            assertThat(r.atRisk()).hasSize(6);
            assertThat(r.usableStock()).isEqualTo(12);
            assertThat(r.reason()).isEqualTo("6 of 12 B+ PLATELETS at risk: soonest expires in 2 days; "
                    + "SC01 uses about 3.0 a day");
        });
    }

    @Test
    void unusedStockIsAtRiskOnlyWithinTheLookAhead() {
        t.add(t.sc01, PLATELETS, B_POS, 1, 2); // 4 days left, never used
        t.add(t.sc01, PLASMA, B_POS, 1, 2); // 364 days left, never used

        List<RiskItem> risks = analyzer.analyze(t.network, TODAY);

        assertThat(risks).singleElement().extracting(RiskItem::component).isEqualTo(PLATELETS);
    }

    @Test
    void wellUsedStockIsNotAtRisk() {
        t.add(t.sc01, PLATELETS, B_POS, 3, 4);
        t.usagePerDay(t.sc01, PLATELETS, B_POS, 5);
        assertThat(analyzer.analyze(t.network, TODAY)).isEmpty();
    }

    @Test
    void risksAreSortedSoonestFirst() {
        t.add(t.sc01, PLATELETS, B_POS, 1, 2); // 4 days left
        t.add(t.hub, PLATELETS, B_POS, 4, 2); // 1 day left
        assertThat(analyzer.analyze(t.network, TODAY)).extracting(r -> r.facility().getId())
                .containsExactly("HUB01", "SC01");
    }

    @Test
    void donorPreferenceTriesIdenticalFirstAndONegativeLast() {
        List<BloodGroup> order = DonorPreference.forRecipient(ComponentType.RED_CELLS, BloodGroup.AB_POS);
        assertThat(order).hasSize(8);
        assertThat(order.get(0)).isEqualTo(BloodGroup.AB_POS);
        assertThat(order.get(order.size() - 1)).isEqualTo(BloodGroup.O_NEG);
        assertThat(DonorPreference.forRecipient(ComponentType.RED_CELLS, BloodGroup.O_NEG))
                .containsExactly(BloodGroup.O_NEG);
    }

    @Test
    void freshnessFollowsDaysSinceLastReport() {
        assertThat(Freshness.of(t.sc01, TODAY)).isEqualTo(Freshness.FRESH);
        t.sc01.markReported(TODAY.minusDays(3));
        assertThat(Freshness.of(t.sc01, TODAY)).isEqualTo(Freshness.STALE);
        t.sc01.markReported(TODAY.minusDays(4));
        assertThat(Freshness.of(t.sc01, TODAY)).isEqualTo(Freshness.UNVERIFIED);
    }

    @Test
    void addedUnitsGetSequentialIdsAndAreAudited() throws Exception {
        List<BloodComponent> added = t.network.addUnits("SC01", PLATELETS, B_POS, TODAY, 2, TODAY);

        assertThat(added).extracting(BloodComponent::getId)
                .containsExactly(UnitIds.format(1000), UnitIds.format(1001));
        assertThat(t.network.snapshot().nextUnitSerial()).isEqualTo(1002);
        assertThat(t.audit.events()).singleElement().extracting(e -> e.type()).isEqualTo(AuditEventType.COLLECTED);
    }

    @Test
    void aDamagedUnitCanBeDiscardedWithAReason() throws Exception {
        BloodComponent unit = t.add(t.sc01, PLATELETS, B_POS, 1, 1).get(0);

        t.network.discardUnit("SC01", unit.getId(), "bag leaking", TODAY);

        assertThat(t.sc01.getInventory().totalUnits()).isZero();
        assertThat(t.network.wastage()).singleElement().satisfies(w -> assertThat(w.unitId()).isEqualTo(unit.getId()));
        assertThat(t.audit.events()).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(AuditEventType.DISCARDED);
            assertThat(e.note()).isEqualTo("bag leaking");
        });
        assertThatThrownBy(() -> t.network.discardUnit("SC01", unit.getId(), "again", TODAY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> t.network.discardUnit("SC01", "X", " ", TODAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnedUnitsGoBackOnTheShelf() throws Exception {
        BloodComponent unit = t.add(t.sc01, PLATELETS, B_POS, 1, 1).get(0);
        t.sc01.getInventory().take(unit.getId());

        t.network.returnUnits("SC01", List.of(unit), "request cancelled", TODAY);

        assertThat(t.sc01.getInventory().find(unit.getId())).isPresent();
        assertThat(t.audit.events()).singleElement().extracting(e -> e.type()).isEqualTo(AuditEventType.RETURNED);
        assertThatThrownBy(() -> t.network.returnUnits("H01", List.of(unit), "x", TODAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotAddUnitsToAHospitalOrFromTheFuture() {
        assertThatThrownBy(() -> t.network.addUnits("H01", PLATELETS, B_POS, TODAY, 1, TODAY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> t.network.addUnits("SC01", PLATELETS, B_POS, TODAY.plusDays(1), 1, TODAY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
