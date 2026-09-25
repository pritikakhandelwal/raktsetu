package com.raktsetu.persistence;

import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.StorageCentre;
import com.raktsetu.domain.model.WastageRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryAndReportTest {

    @TempDir
    Path dir;

    @Test
    void repositorySavesFindsDeletesAndFlushes() throws Exception {
        Path file = dir.resolve("facilities.csv");
        CsvRepository<Facility> repo = new CsvRepository<>(file, DataMappers.FACILITIES);
        repo.save(new StorageCentre("SC01", "One"));
        repo.save(new StorageCentre("SC02", "Two"));
        repo.save(new StorageCentre("SC01", "One renamed"));

        assertThat(repo.findAll()).hasSize(2);
        assertThat(repo.findById("SC01").orElseThrow().getName()).isEqualTo("One renamed");
        assertThat(repo.delete("SC02")).isTrue();
        assertThat(repo.delete("SC02")).isFalse();

        repo.flush();
        CsvRepository<Facility> reloaded = new CsvRepository<>(file, DataMappers.FACILITIES);
        reloaded.load();
        assertThat(reloaded.findAll()).extracting(Facility::getId).containsExactly("SC01");
    }

    @Test
    void reportWriterWritesExportableRows() throws Exception {
        ReportWriter writer = new ReportWriter(dir.resolve("reports"));
        Path file = writer.write("wastage.csv", List.of("date", "facility", "unit", "component", "group"),
                List.of(new WastageRecord(LocalDate.of(2026, 10, 1), "SC01", "RS-000001", ComponentType.PLATELETS,
                        BloodGroup.AB_NEG)));

        assertThat(Files.readAllLines(file)).containsExactly("date,facility,unit,component,group",
                "2026-10-01,SC01,RS-000001,PLATELETS,AB-");
    }

    @Test
    void reportWriterRejectsRowsThatDoNotMatchTheHeader() {
        ReportWriter writer = new ReportWriter(dir);
        assertThatThrownBy(() -> writer.write("bad.csv", List.of("only one column"),
                List.of(new WastageRecord(LocalDate.of(2026, 10, 1), "SC01", "RS-1", ComponentType.PLASMA,
                        BloodGroup.O_POS))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
