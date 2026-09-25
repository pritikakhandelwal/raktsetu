package com.raktsetu.engine;

import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.model.AuditEventType;
import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.TransferOrder;
import com.raktsetu.domain.model.TransferStatus;
import com.raktsetu.engine.strategy.ExpiryFirstStrategy;
import com.raktsetu.engine.strategy.NearestFirstStrategy;
import com.raktsetu.engine.strategy.RedistributionEngine;
import com.raktsetu.engine.strategy.StrategyComparison;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.raktsetu.domain.model.BloodGroup.B_POS;
import static com.raktsetu.domain.model.ComponentType.PLATELETS;
import static com.raktsetu.engine.TestNetwork.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SC02 holds 10 B+ platelets with 2 days left but uses 1 every other day, so 9 are at risk. SC01 (35 min away) uses 4 a
 * day and has 1, so it can take 7 more; HUB01 (30 min away) uses 3 a day and has none, so it can take 6.
 */
class StrategyAndTransferTest {

    private TestNetwork t;
    private final RedistributionEngine engine = RedistributionEngine.withDefaultStrategies();

    @BeforeEach
    void setUp() {
        t = new TestNetwork();
        t.add(t.sc02, PLATELETS, B_POS, 3, 10);
        for (int d = 2; d <= 14; d += 2) {
            t.usage.record(TODAY.minusDays(d), "SC02", PLATELETS, B_POS, 1);
        }
        t.add(t.sc01, PLATELETS, B_POS, 1, 1);
        t.usagePerDay(t.sc01, PLATELETS, B_POS, 4);
        t.usagePerDay(t.hub, PLATELETS, B_POS, 3);
    }

    private static Map<String, Integer> unitsByDestination(List<TransferOrder> orders) {
        return orders.stream().collect(Collectors.groupingBy(o -> o.getDestination().getId(),
                Collectors.summingInt(TransferOrder::unitCount)));
    }

    @Test
    void nearestFirstFillsTheNearestCentreFirst() {
        List<TransferOrder> orders = engine.plan(NearestFirstStrategy.NAME, t.network, TODAY);
        assertThat(unitsByDestination(orders)).containsExactlyInAnyOrderEntriesOf(Map.of("HUB01", 6, "SC01", 3));
    }

    @Test
    void expiryFirstSpreadsUnitsByMostSpareDemand() {
        List<TransferOrder> orders = engine.plan(ExpiryFirstStrategy.NAME, t.network, TODAY);
        assertThat(unitsByDestination(orders)).containsExactlyInAnyOrderEntriesOf(Map.of("SC01", 5, "HUB01", 4));
    }

    @Test
    void comparisonUsesTheSameRisksForEveryStrategy() {
        List<StrategyComparison> comparison = engine.compare(t.network, TODAY);
        assertThat(comparison).extracting(StrategyComparison::strategy)
                .containsExactly(NearestFirstStrategy.NAME, ExpiryFirstStrategy.NAME);
        assertThat(comparison).allSatisfy(c -> {
            assertThat(c.atRiskUnits()).isEqualTo(9);
            assertThat(c.unitsRescued()).isEqualTo(9);
            assertThat(c.stillAtRisk()).isZero();
        });
    }

    @Test
    void planningDoesNotChangeStock() {
        engine.plan(ExpiryFirstStrategy.NAME, t.network, TODAY);
        assertThat(t.sc02.getInventory().totalUnits()).isEqualTo(10);
        assertThat(t.audit.events()).isEmpty();
    }

    @Test
    void unitsAreNeverSentToCentresWithUnverifiedData() {
        t.sc01.markReported(TODAY.minusDays(10));
        List<TransferOrder> orders = engine.plan(ExpiryFirstStrategy.NAME, t.network, TODAY);
        assertThat(unitsByDestination(orders)).containsExactlyEntriesOf(Map.of("HUB01", 6));
    }

    @Test
    void approvingMovesTheUnitsAndAuditsThem() throws Exception {
        TransferOrder order = engine.plan(NearestFirstStrategy.NAME, t.network, TODAY).get(0);
        new TransferService(t.network).approve(order, TODAY);

        assertThat(order.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        for (BloodComponent u : order.getUnits()) {
            assertThat(order.getDestination().getInventory().find(u.getId())).isPresent();
            assertThat(order.getSource().getInventory().find(u.getId())).isEmpty();
        }
        assertThat(t.audit.events()).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(AuditEventType.TRANSFERRED);
            assertThat(e.counterparty()).isEqualTo(order.getDestination().getId());
        });
    }

    @Test
    void approvalFailsSafelyIfAUnitIsNoLongerThere() throws Exception {
        TransferOrder order = engine.plan(NearestFirstStrategy.NAME, t.network, TODAY).get(0);
        t.sc02.getInventory().take(order.getUnits().get(1).getId());
        int before = order.getDestination().getInventory().totalUnits();

        assertThatThrownBy(() -> new TransferService(t.network).approve(order, TODAY))
                .isInstanceOf(RaktSetuException.class)
                .hasMessageContaining("no longer at");
        assertThat(order.getDestination().getInventory().totalUnits()).isEqualTo(before);
        assertThat(order.getStatus()).isEqualTo(TransferStatus.PROPOSED);
    }

    @Test
    void anOrderCannotBeApprovedTwiceOrAfterRejection() throws Exception {
        List<TransferOrder> orders = engine.plan(NearestFirstStrategy.NAME, t.network, TODAY);
        TransferService service = new TransferService(t.network);
        service.approve(orders.get(0), TODAY);
        assertThatThrownBy(() -> service.approve(orders.get(0), TODAY)).isInstanceOf(RaktSetuException.class);
        service.reject(orders.get(1));
        assertThatThrownBy(() -> service.approve(orders.get(1), TODAY)).isInstanceOf(RaktSetuException.class);
    }

    @Test
    void unknownStrategyNameIsRejected() {
        assertThatThrownBy(() -> engine.plan("random", t.network, TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nearest-first");
    }
}
