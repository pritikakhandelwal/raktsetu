package com.raktsetu.persistence;

import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.interfaces.AuditTrail;
import com.raktsetu.domain.model.AuditEvent;
import com.raktsetu.domain.model.AuditEventType;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * An append-only audit log in which every entry carries the SHA-256 hash of its own fields and of the previous
 * entry's hash. Editing, removing or reordering any past entry breaks the chain from that point on, which
 * {@link #verify()} detects (see ADR 0003).
 */
public final class AuditLogger implements AuditTrail {

    /** The "previous hash" of the first entry. */
    public static final String GENESIS = AuditHashing.GENESIS;

    private static final List<String> HEADER = List.of("seq", "date", "type", "facility_id", "counterparty",
            "component", "blood_group", "unit_ids", "note", "prev_hash", "hash");
    private static final int FIELDS_BEFORE_HASH = 10;

    /**
     * Result of checking the chain.
     *
     * @param intact  true if every entry is unmodified
     * @param entries number of entries checked
     * @param message a human-readable summary, naming the first broken entry if any
     */
    public record Verification(boolean intact, int entries, String message) {
    }

    private final Path file;
    private long lastSequence;
    private String lastHash = GENESIS;

    /**
     * Opens (or will create) the log. The chain continues from the last line of an existing file.
     *
     * @throws RaktSetuException if an existing file cannot be read
     */
    public AuditLogger(Path file) throws RaktSetuException {
        this.file = Objects.requireNonNull(file, "file");
        List<String> lines = dataLines();
        if (!lines.isEmpty()) {
            List<String> last = Csv.parseLine(lines.get(lines.size() - 1));
            try {
                lastSequence = Long.parseLong(last.get(0));
                lastHash = last.get(FIELDS_BEFORE_HASH);
            } catch (RuntimeException e) {
                throw new RaktSetuException("The last line of " + file + " is damaged; run verify for details", e);
            }
        }
    }

    public Path file() {
        return file;
    }

    @Override
    public synchronized void record(AuditEvent event) {
        long sequence = lastSequence + 1;
        List<String> fields = AuditHashing.fields(sequence, event, lastHash);
        String hash = AuditHashing.hash(fields);
        List<String> row = new ArrayList<>(fields);
        row.add(hash);
        try {
            if (!Files.exists(file)) {
                Path dir = file.toAbsolutePath().getParent();
                Files.createDirectories(dir);
                Files.writeString(file, Csv.formatLine(HEADER) + "\n", StandardCharsets.UTF_8);
            }
            Files.writeString(file, Csv.formatLine(row) + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not append to audit log " + file, e);
        }
        lastSequence = sequence;
        lastHash = hash;
    }

    /** Every readable entry, oldest first. Lines that cannot be parsed are skipped; {@link #verify()} reports them. */
    public List<AuditRecord> readAll() throws RaktSetuException {
        List<AuditRecord> records = new ArrayList<>();
        for (String line : dataLines()) {
            try {
                List<String> f = Csv.parseLine(line);
                AuditEvent event = new AuditEvent(LocalDate.parse(f.get(1)), AuditEventType.valueOf(f.get(2)),
                        f.get(3), f.get(4), ComponentType.valueOf(f.get(5)), BloodGroup.parse(f.get(6)),
                        Arrays.asList(f.get(7).split(";")), f.get(8));
                records.add(new AuditRecord(Long.parseLong(f.get(0)), event, f.get(9), f.get(10)));
            } catch (RuntimeException e) {
                // skipped here; verify() explains exactly what is wrong
            }
        }
        return records;
    }

    /** Recomputes the whole chain and reports the first entry that does not match. */
    public Verification verify() throws RaktSetuException {
        List<String> lines = dataLines();
        String expectedPrev = GENESIS;
        long expectedSeq = 1;
        for (String line : lines) {
            List<String> f;
            try {
                f = Csv.parseLine(line);
            } catch (IllegalArgumentException e) {
                return broken(expectedSeq, "cannot be read");
            }
            if (f.size() != HEADER.size()) {
                return broken(expectedSeq, "has " + f.size() + " fields instead of " + HEADER.size());
            }
            if (!f.get(0).equals(String.valueOf(expectedSeq))) {
                return broken(expectedSeq, "is missing or out of order (found sequence " + f.get(0) + ")");
            }
            if (!f.get(9).equals(expectedPrev)) {
                return broken(expectedSeq, "does not link to the previous entry");
            }
            String actual = AuditHashing.hash(f.subList(0, FIELDS_BEFORE_HASH));
            if (!actual.equals(f.get(FIELDS_BEFORE_HASH))) {
                return broken(expectedSeq, "was modified after it was written");
            }
            expectedPrev = f.get(FIELDS_BEFORE_HASH);
            expectedSeq++;
        }
        int n = lines.size();
        return new Verification(true, n, "Audit log intact: " + n + " entr" + (n == 1 ? "y" : "ies") + " verified");
    }

    private static Verification broken(long sequence, String problem) {
        String intact = sequence == 1 ? "no earlier entries" : "entries 1-" + (sequence - 1) + " are intact";
        return new Verification(false, (int) sequence - 1,
                "Audit log TAMPERED: entry " + sequence + " " + problem + "; " + intact);
    }

    private List<String> dataLines() throws RaktSetuException {
        if (!Files.exists(file)) {
            return List.of();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RaktSetuException("Could not read audit log " + file + ": " + e.getMessage(), e);
        }
        List<String> data = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) { // line 0 is the header
            if (!lines.get(i).isBlank()) {
                data.add(lines.get(i));
            }
        }
        return data;
    }
}
