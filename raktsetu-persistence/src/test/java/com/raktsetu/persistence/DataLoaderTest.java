package com.raktsetu.persistence;

import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.Hospital;
import com.raktsetu.domain.model.HubBloodCentre;
import com.raktsetu.domain.model.LoadResult;
import com.raktsetu.domain.model.NetworkData;
import com.raktsetu.domain.model.Platelets;
import com.raktsetu.domain.model.RedCellConcentrate;
import com.raktsetu.domain.model.Route;
import com.raktsetu.domain.model.RouteEntry;
import com.raktsetu.domain.model.UsageHistory;
import com.raktsetu.domain.model.WastageRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataLoaderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    @TempDir
    Path dir;

    private NetworkData sampleNetwork() {
        HubBloodCentre hub = new HubBloodCentre("HUB01", "Hub, Pune", TODAY);
        hub.getInventory().add(new RedCellConcentrate("RS-000001", BloodGroup.O_NEG, TODAY.minusDays(3)));
        hub.getInventory().add(new Platelets("RS-000002", BloodGroup.B_POS, TODAY.minusDays(1)));
        hub.getInventory().add(new RedCellConcentrate("RS-000003", BloodGroup.A_POS, TODAY.minusDays(2),
                TODAY.plusDays(10)));
        Hospital hospital = new Hospital("H01", "City Hospital", null);
        UsageHistory usage = new UsageHistory();
        usage.record(TODAY.minusDays(1), "HUB01", ComponentType.PLATELETS, BloodGroup.B_POS, 3);
        return new NetworkData(List.of(hub, hospital), List.of(new RouteEntry("HUB01", "H01", new Route(12, 25))),
                usage, List.of(new WastageRecord(TODAY.minusDays(2), "HUB01", "RS-000000", ComponentType.PLATELETS,
                BloodGroup.O_POS)), 4);
    }

    @Test
    void savedDataLoadsBackIdentically() throws Exception {
        DataLoader loader = new DataLoader(dir);
        loader.save(sampleNetwork());

        LoadResult result = loader.load();

        assertThat(result.problems()).isEmpty();
        NetworkData data = result.data();
        assertThat(data.facilities()).extracting(Facility::getId).containsExactly("HUB01", "H01");
        Facility hub = data.facilities().get(0);
        assertThat(hub.getName()).isEqualTo("Hub, Pune");
        assertThat(hub.getLastReportedOn()).isEqualTo(TODAY);
        assertThat(hub.getInventory().allUnits()).extracting(BloodComponent::getId)
                .containsExactlyInAnyOrder("RS-000001", "RS-000002", "RS-000003");
        assertThat(hub.getInventory().find("RS-000003").orElseThrow().getExpiryDate()).isEqualTo(TODAY.plusDays(10));
        assertThat(data.routes()).containsExactly(new RouteEntry("HUB01", "H01", new Route(12, 25)));
        assertThat(data.usage().entries()).hasSize(1);
        assertThat(data.wastage()).hasSize(1);
        assertThat(data.nextUnitSerial()).isEqualTo(4);
    }

    @Test
    void corruptedRowIsReportedAndOtherRowsStillLoad() throws Exception {
        DataLoader loader = new DataLoader(dir);
        loader.save(sampleNetwork());
        Path units = dir.resolve(DataLoader.UNITS);
        Files.writeString(units, Files.readString(units) + "RS-000009,HUB01,RED_CELLS,Q+,2026-09-30,\n");

        LoadResult result = loader.load();

        assertThat(result.problems()).singleElement().asString()
                .contains("units.csv, line 5").contains("Unknown blood group");
        assertThat(result.data().facilities().get(0).getInventory().totalUnits()).isEqualTo(3);
    }

    @Test
    void unitsAtUnknownOrNonStoringFacilitiesAreSkipped() throws Exception {
        DataLoader loader = new DataLoader(dir);
        loader.save(sampleNetwork());
        Path units = dir.resolve(DataLoader.UNITS);
        Files.writeString(units, Files.readString(units)
                + "RS-000010,NOWHERE,PLASMA,AB+,2026-09-30,\n"
                + "RS-000011,H01,PLASMA,AB+,2026-09-30,\n");

        LoadResult result = loader.load();

        assertThat(result.problems()).hasSize(2);
        assertThat(result.problems().get(0)).contains("unknown facility 'NOWHERE'");
        assertThat(result.problems().get(1)).contains("does not store blood");
    }

    @Test
    void duplicateUnitIdsAreReported() throws Exception {
        DataLoader loader = new DataLoader(dir);
        loader.save(sampleNetwork());
        Path units = dir.resolve(DataLoader.UNITS);
        Files.writeString(units, Files.readString(units) + "RS-000001,HUB01,PLASMA,AB+,2026-09-30,\n");

        assertThat(loader.load().problems()).singleElement().asString().contains("duplicate ID 'RS-000001'");
    }

    @Test
    void missingDataFolderIsAClearError() {
        DataLoader loader = new DataLoader(dir.resolve("empty"));
        assertThat(loader.hasData()).isFalse();
        assertThatThrownBy(loader::load).isInstanceOf(RaktSetuException.class).hasMessageContaining("No data found");
    }

    @Test
    void withoutAStateFileTheNextSerialFollowsTheHighestUnitId() throws Exception {
        DataLoader loader = new DataLoader(dir);
        loader.save(sampleNetwork());
        Files.delete(dir.resolve(DataLoader.STATE));

        assertThat(loader.load().data().nextUnitSerial()).isEqualTo(4);
    }
}
