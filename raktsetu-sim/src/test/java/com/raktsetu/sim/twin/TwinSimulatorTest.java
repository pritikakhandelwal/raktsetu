package com.raktsetu.sim.twin;

import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.FacilityType;
import com.raktsetu.domain.model.HubBloodCentre;
import com.raktsetu.domain.model.Platelets;
import com.raktsetu.domain.model.Route;
import com.raktsetu.domain.model.RouteEntry;
import com.raktsetu.domain.model.UsageHistory;
import com.raktsetu.engine.BloodNetwork;
import com.raktsetu.engine.strategy.ExpiryFirstStrategy;
import com.raktsetu.engine.strategy.NearestFirstStrategy;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwinSimulatorTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    /**
     * A hub and a city centre near two busy city hospitals, and a remote rural centre that receives the same routine
     * supply but serves one small hospital: the mismatch that redistribution is meant to fix.
     */
    static NetworkSpec smallSpec() {
        List<Site> sites = List.of(
                new Site("HUB", "Hub", FacilityType.HUB, 0, 0, 0, 0.4, false),
                new Site("SC1", "City centre", FacilityType.STORAGE, 0, 0, 0, 0.3, false),
                new Site("SC2", "Rural centre", FacilityType.STORAGE, 0, 0, 0, 0.3, true),
                new Site("H1", "City hospital", FacilityType.HOSPITAL, 0, 0, 1.5, 0, false),
                new Site("H2", "General hospital", FacilityType.HOSPITAL, 0, 0, 1.0, 0, false),
                new Site("H3", "Rural hospital", FacilityType.HOSPITAL, 0, 0, 0.25, 0, true));
        List<RouteEntry> routes = new ArrayList<>();
        int[][] m = {
                // HUB SC1 SC2 H1 H2 H3   (travel minutes; km is roughly half)
                {0, 20, 200, 15, 25, 210},
                {20, 0, 210, 10, 20, 220},
                {200, 210, 0, 205, 215, 10},
                {15, 10, 205, 0, 30, 215},
                {25, 20, 215, 30, 0, 225},
                {210, 220, 10, 215, 225, 0}};
        for (int i = 0; i < sites.size(); i++) {
            for (int j = i + 1; j < sites.size(); j++) {
                routes.add(new RouteEntry(sites.get(i).id(), sites.get(j).id(), new Route(m[i][j] / 2, m[i][j])));
            }
        }
        return new NetworkSpec("small", sites, routes, Map.of(ComponentType.RED_CELLS, 6.0,
                ComponentType.PLATELETS, 2.5, ComponentType.PLASMA, 3.0, ComponentType.WHOLE_BLOOD, 0.5),
                NetworkSpec.uniformSupply(1.10));
    }

    private static TwinSettings settings(double compliance, int replication) {
        return new TwinSettings(START, 45, 120, compliance, 42, replication);
    }

    @Test
    void sameSettingsGiveIdenticalResults() throws Exception {
        NetworkSpec spec = smallSpec();
        for (TwinPolicy p : List.of(TwinPolicy.NONE, TwinPolicy.strategy(ExpiryFirstStrategy.NAME))) {
            TwinResult a = new TwinSimulator(spec, settings(0.6, 3)).run(p);
            TwinResult b = new TwinSimulator(spec, settings(0.6, 3)).run(p);
            assertThat(a).isEqualTo(b);
        }
    }

    @Test
    void differentReplicationsDiffer() throws Exception {
        TwinResult a = new TwinSimulator(smallSpec(), settings(1.0, 1)).run(TwinPolicy.NONE);
        TwinResult b = new TwinSimulator(smallSpec(), settings(1.0, 2)).run(TwinPolicy.NONE);
        assertThat(a.unitsCollected()).isNotEqualTo(b.unitsCollected());
    }

    @Test
    void everyUnitIsAccountedFor() throws Exception {
        TwinSimulator.Run run = new TwinSimulator(smallSpec(), settings(0.5, 1))
                .run(TwinPolicy.strategy(ExpiryFirstStrategy.NAME), true);
        assertThat(run.events()).isNotEmpty();
        assertThat(LedgerCheck.verify(run.events(), run.truth())).isEmpty();
    }

    @Test
    void ledgerCheckCatchesAMissingUnit() throws Exception {
        TwinSimulator.Run run = new TwinSimulator(smallSpec(), new TwinSettings(START, 0, 10, 1.0, 42, 1))
                .run(TwinPolicy.NONE, true);
        var anyCentre = run.truth().facility("HUB").getInventory();
        anyCentre.take(anyCentre.allUnits().get(0).getId());
        assertThat(LedgerCheck.verify(run.events(), run.truth())).isNotEmpty();
    }

    @Test
    void redistributionReducesWastageWithFullInformation() throws Exception {
        TwinSimulator sim = new TwinSimulator(smallSpec(), settings(1.0, 1));
        TwinResult none = sim.run(TwinPolicy.NONE);
        TwinResult expiryFirst = sim.run(TwinPolicy.strategy(ExpiryFirstStrategy.NAME));
        TwinResult nearestFirst = sim.run(TwinPolicy.strategy(NearestFirstStrategy.NAME));

        assertThat(none.transferUnitsPlanned()).isZero();
        assertThat(expiryFirst.outdateRate()).isLessThan(none.outdateRate());
        assertThat(nearestFirst.outdateRate()).isLessThan(none.outdateRate());
        assertThat(expiryFirst.unitsCollected()).isEqualTo(none.unitsCollected());
        assertThat(expiryFirst.unitsRequested()).isEqualTo(none.unitsRequested());
    }

    @Test
    void withFullInformationEveryPlannedUnitCanBeMoved() throws Exception {
        TwinResult r = new TwinSimulator(smallSpec(), settings(1.0, 1)).run(TwinPolicy.strategy(ExpiryFirstStrategy.NAME));
        assertThat(r.transferUnitsPlanned()).isPositive();
        assertThat(r.transferUnitsMoved()).isEqualTo(r.transferUnitsPlanned());
    }

    @Test
    void staleReportsMakePlannedTransfersFail() throws Exception {
        TwinResult r = new TwinSimulator(smallSpec(), settings(0.2, 1)).run(TwinPolicy.strategy(ExpiryFirstStrategy.NAME));
        assertThat(r.transferUnitsMoved()).isLessThan(r.transferUnitsPlanned());
    }

    @Test
    void onlyDaysAfterTheWarmUpAreMeasured() throws Exception {
        TwinResult r = new TwinSimulator(smallSpec(), settings(1.0, 1)).run(TwinPolicy.NONE);
        double expectedPerDay = 0;
        for (ComponentType t : ComponentType.values()) {
            expectedPerDay += smallSpec().totalDailyDemand(t) * 1.10;
        }
        assertThat(r.days()).isEqualTo(120);
        assertThat((double) r.unitsCollected()).isBetween(expectedPerDay * 120 * 0.9, expectedPerDay * 120 * 1.1);
        assertThat(r.fillRate()).isBetween(0.0, 1.0);
        assertThat(r.toCsvRow()).hasSameSizeAs(TwinResult.CSV_HEADER);
    }

    @Test
    void complianceControlsWhoReports() {
        Schedule never = Schedule.generate(smallSpec(), new TwinSettings(START, 0, 20, 0.0, 42, 1));
        Schedule always = Schedule.generate(smallSpec(), new TwinSettings(START, 0, 20, 1.0, 42, 1));
        assertThat(never.day(0).reporting()).containsExactlyInAnyOrder("HUB", "SC1", "SC2");
        for (int d = 1; d < 20; d++) {
            assertThat(never.day(d).reporting()).isEmpty();
            assertThat(always.day(d).reporting()).hasSize(3);
        }
    }

    @Test
    void thePlannerSeesOnlyWhatWasReported() {
        HubBloodCentre truth = new HubBloodCentre("HUB", "Hub", START);
        truth.getInventory().add(new Platelets("U1", BloodGroup.O_POS, START));
        UsageHistory usage = new UsageHistory();
        usage.record(START, "HUB", ComponentType.PLATELETS, BloodGroup.O_POS, 4);
        ObservedState observed = new ObservedState();
        observed.report(truth, usage, START.plusDays(1));
        truth.getInventory().add(new Platelets("U2", BloodGroup.O_POS, START.plusDays(1)));

        BloodNetwork view = observed.view(List.of(truth), List.of());

        assertThat(view.storingFacilities().get(0).getInventory().totalUnits()).isEqualTo(1);
        assertThat(view.storingFacilities().get(0).getLastReportedOn()).isEqualTo(START.plusDays(1));
        assertThat(view.usage().averageDaily("HUB", ComponentType.PLATELETS, BloodGroup.O_POS, START.plusDays(1), 1))
                .isEqualTo(4.0);
        assertThat(truth.getInventory().totalUnits()).isEqualTo(2);
    }

    @Test
    void settingsAndSpecsAreValidated() {
        assertThatThrownBy(() -> new TwinSettings(START, 0, 0, 1.0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TwinSettings(START, 0, 10, 1.5, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TwinPolicy.strategy("random")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NetworkSpec("x", List.of(smallSpec().sites().get(0)), List.of(), Map.of(),
                NetworkSpec.uniformSupply(1.0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
