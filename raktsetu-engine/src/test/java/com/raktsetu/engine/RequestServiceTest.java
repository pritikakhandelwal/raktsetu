package com.raktsetu.engine;

import com.raktsetu.domain.exception.FacilityNotFoundException;
import com.raktsetu.domain.exception.IncompatibleBloodException;
import com.raktsetu.domain.exception.InsufficientStockException;
import com.raktsetu.domain.exception.UnitExpiredException;
import com.raktsetu.domain.model.AuditEvent;
import com.raktsetu.domain.model.AuditEventType;
import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodRequest;
import com.raktsetu.domain.model.Urgency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.raktsetu.domain.model.BloodGroup.AB_POS;
import static com.raktsetu.domain.model.BloodGroup.A_NEG;
import static com.raktsetu.domain.model.BloodGroup.A_POS;
import static com.raktsetu.domain.model.BloodGroup.O_NEG;
import static com.raktsetu.domain.model.BloodGroup.O_POS;
import static com.raktsetu.domain.model.ComponentType.PLASMA;
import static com.raktsetu.domain.model.ComponentType.PLATELETS;
import static com.raktsetu.domain.model.ComponentType.RED_CELLS;
import static com.raktsetu.engine.TestNetwork.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestServiceTest {

    private TestNetwork t;
    private RequestService service;

    @BeforeEach
    void setUp() {
        t = new TestNetwork();
        service = new RequestService(t.network);
    }

    private static List<String> ids(RequestOutcome outcome) {
        return outcome.allocations().stream().flatMap(a -> a.units().stream()).map(BloodComponent::getId).toList();
    }

    @Test
    void servesONegativeFromTheNearestCentreSoonestExpiringFirst() throws Exception {
        List<BloodComponent> near = t.add(t.sc01, RED_CELLS, O_NEG, 2, 1);
        List<BloodComponent> nearOld = t.add(t.sc01, RED_CELLS, O_NEG, 20, 1);
        t.add(t.hub, RED_CELLS, O_NEG, 30, 5);

        RequestOutcome outcome = service.handle(new BloodRequest("H01", RED_CELLS, O_NEG, 1), TODAY);

        assertThat(ids(outcome)).containsExactly(nearOld.get(0).getId());
        assertThat(outcome.allocations().get(0).source()).isEqualTo(t.sc01);
        assertThat(outcome.allocations().get(0).route().travelMinutes()).isEqualTo(10);
        assertThat(t.sc01.getInventory().units(RED_CELLS, O_NEG)).containsExactly(near.get(0));
    }

    @Test
    void emergencyUsesACompatibleSubstituteNearbyBeforeTravelling() throws Exception {
        t.add(t.sc01, RED_CELLS, O_NEG, 5, 2);
        t.add(t.hub, RED_CELLS, O_POS, 5, 5);

        RequestOutcome outcome = service.handle(new BloodRequest("H01", RED_CELLS, O_POS, 2), TODAY);

        assertThat(outcome.usedSubstitutes()).isTrue();
        Allocation a = outcome.allocations().get(0);
        assertThat(a.source()).isEqualTo(t.sc01);
        assertThat(a.donorGroup()).isEqualTo(O_NEG);
        assertThat(a.compatibility().reason()).contains("compatible");
    }

    @Test
    void routineRequestPrefersTheExactGroupFurtherAway() throws Exception {
        t.add(t.sc01, RED_CELLS, O_NEG, 5, 2);
        t.add(t.hub, RED_CELLS, O_POS, 5, 5);

        RequestOutcome outcome = service.handle(
                new BloodRequest("H01", RED_CELLS, O_POS, 2, Urgency.ROUTINE), TODAY);

        assertThat(outcome.usedSubstitutes()).isFalse();
        assertThat(outcome.allocations()).singleElement().extracting(Allocation::source).isEqualTo(t.hub);
    }

    @Test
    void combinesFacilitiesWhenOneIsNotEnough() throws Exception {
        t.add(t.sc01, RED_CELLS, O_POS, 5, 1);
        t.add(t.hub, RED_CELLS, O_POS, 5, 2);

        RequestOutcome outcome = service.handle(new BloodRequest("H01", RED_CELLS, O_POS, 3), TODAY);

        assertThat(outcome.unitsIssued()).isEqualTo(3);
        assertThat(outcome.allocations()).extracting(Allocation::source).containsExactly(t.sc01, t.hub);
    }

    @Test
    void insufficientStockIssuesNothingAndSaysWhereStockIs() {
        t.add(t.sc01, RED_CELLS, A_POS, 5, 1);
        t.add(t.hub, RED_CELLS, A_NEG, 5, 1);

        assertThatThrownBy(() -> service.handle(new BloodRequest("H01", RED_CELLS, A_POS, 3), TODAY))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("requested 3, available 2")
                .hasMessageContaining("SC01: 1, HUB01: 1");
        assertThat(t.sc01.getInventory().totalUnits() + t.hub.getInventory().totalUnits()).isEqualTo(2);
    }

    @Test
    void stockBeyondTheTravelLimitIsNotUsed() {
        t.add(t.sc03, RED_CELLS, O_POS, 5, 5);
        assertThatThrownBy(() -> service.handle(new BloodRequest("H01", RED_CELLS, O_POS, 1), TODAY))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void unverifiedCentresAreSearchedLast() throws Exception {
        t.sc01.markReported(TODAY.minusDays(10));
        t.add(t.sc01, RED_CELLS, O_POS, 5, 1);
        t.add(t.hub, RED_CELLS, O_POS, 5, 1);

        RequestOutcome outcome = service.handle(new BloodRequest("H01", RED_CELLS, O_POS, 1), TODAY);

        assertThat(outcome.allocations().get(0).source()).isEqualTo(t.hub);
    }

    @Test
    void keepsONegativeForPatientsWhoNeedIt() throws Exception {
        t.add(t.sc01, RED_CELLS, O_NEG, 5, 3);
        t.add(t.sc01, RED_CELLS, A_POS, 5, 3);

        RequestOutcome outcome = service.handle(new BloodRequest("H01", RED_CELLS, AB_POS, 2), TODAY);

        assertThat(outcome.allocations()).singleElement().extracting(Allocation::donorGroup).isEqualTo(A_POS);
    }

    @Test
    void plasmaFollowsTheReversedRule() throws Exception {
        t.add(t.sc01, PLASMA, AB_POS, 5, 1);
        t.add(t.sc01, PLASMA, A_POS, 5, 1);

        RequestOutcome outcome = service.handle(new BloodRequest("H01", PLASMA, O_POS, 2), TODAY);

        assertThat(outcome.allocations()).extracting(Allocation::donorGroup).containsExactly(A_POS, AB_POS);
        assertThatThrownBy(() -> service.handle(new BloodRequest("H01", PLASMA, AB_POS, 1), TODAY))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void recordsUsageAndAudit() throws Exception {
        t.add(t.sc01, RED_CELLS, O_POS, 5, 2);

        service.handle(new BloodRequest("H01", RED_CELLS, O_POS, 2), TODAY);

        assertThat(t.usage.averageDaily("SC01", RED_CELLS, O_POS, TODAY.plusDays(1), 1)).isEqualTo(2.0);
        AuditEvent event = t.audit.events().get(t.audit.events().size() - 1);
        assertThat(event.type()).isEqualTo(AuditEventType.ISSUED);
        assertThat(event.counterparty()).isEqualTo("H01");
        assertThat(event.unitIds()).hasSize(2);
    }

    @Test
    void expiredUnitsAreDiscardedBeforeIssuing() throws Exception {
        t.add(t.sc01, PLATELETS, O_POS, 6, 1);
        List<BloodComponent> fresh = t.add(t.sc01, PLATELETS, O_POS, 1, 1);

        RequestOutcome outcome = service.handle(new BloodRequest("H01", PLATELETS, O_POS, 1), TODAY);

        assertThat(ids(outcome)).containsExactly(fresh.get(0).getId());
        assertThat(t.network.wastage()).hasSize(1);
        assertThat(t.audit.events()).extracting(AuditEvent::type).contains(AuditEventType.DISCARDED);
    }

    @Test
    void unknownHospitalIsReported() {
        assertThatThrownBy(() -> service.handle(new BloodRequest("H99", RED_CELLS, O_POS, 1), TODAY))
                .isInstanceOf(FacilityNotFoundException.class)
                .hasMessageContaining("H99");
    }

    @Test
    void issuingASpecificUnsafeUnitIsBlocked() {
        BloodComponent abPositive = t.add(t.sc01, RED_CELLS, AB_POS, 5, 1).get(0);

        assertThatThrownBy(() -> service.issueUnit("SC01", abPositive.getId(), "H01", O_NEG, TODAY))
                .isInstanceOf(IncompatibleBloodException.class)
                .hasMessageContaining("RhD");
        assertThat(t.sc01.getInventory().find(abPositive.getId())).isPresent();
    }

    @Test
    void issuingASpecificExpiredUnitIsBlocked() {
        BloodComponent old = t.add(t.sc01, PLATELETS, O_POS, 6, 1).get(0);
        assertThatThrownBy(() -> service.issueUnit("SC01", old.getId(), "H01", O_POS, TODAY))
                .isInstanceOf(UnitExpiredException.class);
    }

    @Test
    void issuingASpecificSafeUnitWorks() throws Exception {
        BloodComponent unit = t.add(t.sc01, RED_CELLS, O_NEG, 5, 1).get(0);
        Allocation a = service.issueUnit("SC01", unit.getId(), "H01", AB_POS, TODAY);
        assertThat(a.units()).containsExactly(unit);
        assertThat(t.sc01.getInventory().totalUnits()).isZero();
    }
}
