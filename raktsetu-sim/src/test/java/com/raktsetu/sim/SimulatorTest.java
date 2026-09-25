package com.raktsetu.sim;

import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.NetworkData;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);

    @Test
    void sampleNetworkIsDeterministicAndRelativeToToday() {
        NetworkData a = SampleNetworkFactory.create(START, 42);
        NetworkData b = SampleNetworkFactory.create(START, 42);
        NetworkData later = SampleNetworkFactory.create(START.plusDays(100), 42);

        assertThat(describe(a)).isEqualTo(describe(b));
        BloodComponent first = a.facilities().get(0).getInventory().allUnits().get(0);
        BloodComponent firstLater = later.facilities().get(0).getInventory().allUnits().get(0);
        assertThat(firstLater.getCollectedOn()).isEqualTo(first.getCollectedOn().plusDays(100));
    }

    @Test
    void sampleNetworkHasTheDemoStory() {
        NetworkData data = SampleNetworkFactory.create(START, 42);
        Set<String> ids = new HashSet<>();
        int expired = 0;
        for (Facility f : data.facilities()) {
            if (!f.canStore()) {
                continue;
            }
            for (BloodComponent u : f.getInventory().allUnits()) {
                assertThat(ids.add(u.getId())).as("unique id %s", u.getId()).isTrue();
                if (u.isExpired(START)) {
                    expired++;
                }
            }
        }
        assertThat(expired).as("units that expired overnight").isEqualTo(3);
        assertThat(data.facilities()).filteredOn(f -> f.getId().equals("SC03"))
                .singleElement().extracting(Facility::getLastReportedOn).isEqualTo(START.minusDays(5));
        assertThat(data.nextUnitSerial()).isEqualTo(ids.size() + 1);
    }

    @Test
    void sameSeedGivesIdenticalResults() throws Exception {
        List<SimulationResult> first = new Simulator(START, 30, 42).compareAll();
        List<SimulationResult> second = new Simulator(START, 30, 42).compareAll();
        assertThat(first).isEqualTo(second);
    }

    @Test
    void redistributionReducesWastageWithoutHurtingSupply() throws Exception {
        List<SimulationResult> results = new Simulator(START, 30, 42).compareAll();
        SimulationResult none = results.get(0);

        assertThat(none.unitsTransferred()).isZero();
        for (SimulationResult withTransfers : results.subList(1, 3)) {
            assertThat(withTransfers.unitsTransferred()).isPositive();
            assertThat(withTransfers.unitsExpired()).isLessThan(none.unitsExpired());
            assertThat(withTransfers.requestsUnmet()).isLessThanOrEqualTo(none.requestsUnmet());
            assertThat(withTransfers.unitsCollected()).isEqualTo(none.unitsCollected());
        }
    }

    private static List<String> describe(NetworkData data) {
        return data.facilities().stream()
                .flatMap(f -> f.canStore() ? f.getInventory().allUnits().stream().map(u -> f.getId() + u) :
                        java.util.stream.Stream.of(f.getId()))
                .toList();
    }
}
