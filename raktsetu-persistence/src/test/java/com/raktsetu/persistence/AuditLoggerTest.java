package com.raktsetu.persistence;

import com.raktsetu.domain.model.AuditEvent;
import com.raktsetu.domain.model.AuditEventType;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLoggerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    @TempDir
    Path dir;

    private Path file;

    @BeforeEach
    void setUp() {
        file = dir.resolve("audit_log.csv");
    }

    private static AuditEvent issue(String unit, String note) {
        return new AuditEvent(DAY, AuditEventType.ISSUED, "SC03", "H02", ComponentType.RED_CELLS, BloodGroup.O_POS,
                List.of(unit), note);
    }

    private void writeThree() throws Exception {
        AuditLogger log = new AuditLogger(file);
        log.record(issue("RS-000001", "exact match"));
        log.record(issue("RS-000002", "substituted O- for O+, with commas"));
        log.record(issue("RS-000003", ""));
    }

    @Test
    void untouchedLogVerifies() throws Exception {
        writeThree();
        AuditLogger.Verification v = new AuditLogger(file).verify();
        assertThat(v.intact()).isTrue();
        assertThat(v.entries()).isEqualTo(3);
    }

    @Test
    void editingAnEntryIsDetected() throws Exception {
        writeThree();
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Files.writeString(file, text.replace("RS-000002", "RS-000099"), StandardCharsets.UTF_8);

        AuditLogger.Verification v = new AuditLogger(file).verify();

        assertThat(v.intact()).isFalse();
        assertThat(v.message()).contains("entry 2 was modified").contains("entries 1-1 are intact");
    }

    @Test
    void deletingAnEntryIsDetected() throws Exception {
        writeThree();
        List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        lines.remove(2); // header is line 0, so this removes entry 2
        Files.write(file, lines, StandardCharsets.UTF_8);

        assertThat(new AuditLogger(file).verify().message()).contains("entry 2 is missing or out of order");
    }

    @Test
    void reopeningContinuesTheChain() throws Exception {
        writeThree();
        AuditLogger reopened = new AuditLogger(file);
        reopened.record(issue("RS-000004", "after restart"));

        assertThat(reopened.verify().intact()).isTrue();
        List<AuditRecord> records = reopened.readAll();
        assertThat(records).extracting(AuditRecord::sequence).containsExactly(1L, 2L, 3L, 4L);
        assertThat(records.get(1).event().note()).isEqualTo("substituted O- for O+, with commas");
        assertThat(records.get(0).prevHash()).isEqualTo(AuditLogger.GENESIS);
        assertThat(records.get(1).prevHash()).isEqualTo(records.get(0).hash());
    }

    @Test
    void emptyLogIsIntact() throws Exception {
        assertThat(new AuditLogger(file).verify().intact()).isTrue();
    }
}
