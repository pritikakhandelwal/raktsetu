package com.raktsetu.bench;

import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.NetworkData;
import com.raktsetu.domain.model.RouteEntry;
import com.raktsetu.engine.BloodNetwork;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheticNetworkTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    @Test
    void hasTheRequestedSize() {
        NetworkData data = SyntheticNetwork.generate(new SyntheticNetwork.Spec(30, 2, 100, 1), TODAY);
        assertThat(data.facilities()).hasSize(90);
        assertThat(data.facilities().stream().filter(Facility::canStore)).hasSize(30);
        assertThat(data.facilities().stream().filter(Facility::canStore).mapToInt(f -> f.getInventory().totalUnits())
                .sum()).isEqualTo(3000);
        assertThat(data.facilities().stream().filter(f -> f.getId().startsWith("HUB"))).hasSize(3);
    }

    @Test
    void everyHospitalCanReachItsOwnCentreQuickly() {
        BloodNetwork network = new BloodNetwork(SyntheticNetwork.generate(SyntheticNetwork.Spec.ofCentres(50), TODAY),
                new NoOpAuditTrail());
        for (Facility h : network.facilities()) {
            if (h.canStore()) {
                continue;
            }
            String centreNumber = h.getId().substring(1, 5);
            String centre = network.facilities().stream().filter(Facility::canStore)
                    .filter(f -> f.getId().endsWith(centreNumber)).findFirst().orElseThrow().getId();
            assertThat(network.route(centre, h.getId())).hasValueSatisfying(
                    r -> assertThat(r.travelMinutes()).isLessThanOrEqualTo(40));
        }
    }

    @Test
    void routesAreSparseAndNeverBetweenHospitals() {
        NetworkData data = SyntheticNetwork.generate(SyntheticNetwork.Spec.ofCentres(400), TODAY);
        Set<String> hospitals = new HashSet<>();
        data.facilities().stream().filter(f -> !f.canStore()).forEach(f -> hospitals.add(f.getId()));
        int n = data.facilities().size();
        assertThat(data.routes().size()).isLessThan(n * (n - 1) / 20);
        for (RouteEntry r : data.routes()) {
            assertThat(hospitals.contains(r.fromId()) && hospitals.contains(r.toId())).isFalse();
            assertThat(r.route().distanceKm()).isLessThanOrEqualTo((int) (SyntheticNetwork.MAX_ROUTE_KM * 1.35) + 2);
        }
    }

    @Test
    void theSameSeedGivesTheSameNetwork() {
        var spec = new SyntheticNetwork.Spec(20, 1, 50, 9);
        assertThat(SyntheticNetwork.generate(spec, TODAY).routes())
                .isEqualTo(SyntheticNetwork.generate(spec, TODAY).routes());
    }
}
