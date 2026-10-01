package com.raktsetu.server.db;

import com.raktsetu.domain.model.AuditEvent;
import com.raktsetu.domain.model.AuditEventType;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.persistence.AuditHashing;
import com.raktsetu.persistence.AuditRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * The hash-chained audit log, stored in the {@code audit_log} table. Each row stores the hash of its own fields and of
 * the previous row's hash (the same format as the file log), so editing, deleting or reordering rows is detected.
 */
@Repository
public class AuditRepository {

    /** The most entries one page of the audit log returns, so a request cannot load the whole log at once. */
    public static final int MAX_PAGE = 500;

    /** Result of checking the chain. */
    public record Verification(boolean intact, int entries, String message) {
    }

    private final JdbcClient jdbc;

    public AuditRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Appends events in order, continuing the chain. Call inside the command's transaction. */
    public void append(List<AuditEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        var last = jdbc.sql("select seq, hash from audit_log order by seq desc limit 1")
                .query((rs, n) -> new Object[] {rs.getLong("seq"), rs.getString("hash")}).optional();
        long seq = last.map(r -> (Long) r[0]).orElse(0L);
        String prev = last.map(r -> (String) r[1]).orElse(AuditHashing.GENESIS);
        for (AuditEvent e : events) {
            seq++;
            String hash = AuditHashing.hash(seq, e, prev);
            jdbc.sql("insert into audit_log (seq, event_day, type, facility_id, counterparty, component, blood_group, "
                            + "unit_ids, note, prev_hash, hash) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                    .params(seq, e.date(), e.type().name(), e.facilityId(), e.counterparty(), e.component().name(),
                            e.group().label(), String.join(";", e.unitIds()), e.note(), prev, hash)
                    .update();
            prev = hash;
        }
    }

    public int count() {
        return jdbc.sql("select count(*) from audit_log").query(Integer.class).single();
    }

    /** The newest entries, newest first: at least 1 and at most {@value #MAX_PAGE}, whatever is asked for. */
    public List<AuditRecord> latest(int limit) {
        return jdbc.sql("select * from audit_log order by seq desc limit ?")
                .param(Math.max(1, Math.min(limit, MAX_PAGE)))
                .query((rs, n) -> new AuditRecord(rs.getLong("seq"), new AuditEvent(
                        rs.getObject("event_day", LocalDate.class), AuditEventType.valueOf(rs.getString("type")),
                        rs.getString("facility_id"), rs.getString("counterparty"),
                        ComponentType.valueOf(rs.getString("component")), BloodGroup.parse(rs.getString("blood_group")),
                        Arrays.asList(rs.getString("unit_ids").split(";")), rs.getString("note")),
                        rs.getString("prev_hash"), rs.getString("hash")))
                .list();
    }

    /** Recomputes the whole chain and reports the first row that does not match. */
    public Verification verify() {
        return check(rowsAfter(0), ChainPoint.START).verification();
    }

    /**
     * Checks only the entries after {@code from}, continuing the chain from its hash. Cheap enough to run on every
     * page load; entries at or before {@code from} were checked earlier and are re-checked by {@link #verify()}.
     */
    public Checked verifyAfter(ChainPoint from) {
        return check(rowsAfter(from.sequence()), from);
    }

    /** The last entry of a verified stretch of the chain: its sequence number and hash. */
    public record ChainPoint(long sequence, String hash) {
        public static final ChainPoint START = new ChainPoint(0, AuditHashing.GENESIS);
    }

    /** A verification and, if intact, where the verified chain now ends. */
    public record Checked(Verification verification, ChainPoint end) {
    }

    private List<AuditRecord> rowsAfter(long seq) {
        return jdbc.sql("select * from audit_log where seq > ? order by seq").param(seq)
                .query((rs, n) -> new AuditRecord(rs.getLong("seq"), new AuditEvent(
                        rs.getObject("event_day", LocalDate.class), AuditEventType.valueOf(rs.getString("type")),
                        rs.getString("facility_id"), rs.getString("counterparty"),
                        ComponentType.valueOf(rs.getString("component")), BloodGroup.parse(rs.getString("blood_group")),
                        Arrays.asList(rs.getString("unit_ids").split(";")), rs.getString("note")),
                        rs.getString("prev_hash"), rs.getString("hash")))
                .list();
    }

    private static Checked check(List<AuditRecord> rows, ChainPoint from) {
        String prev = from.hash();
        long expected = from.sequence() + 1;
        for (AuditRecord r : rows) {
            String problem = null;
            if (r.sequence() != expected) {
                problem = "is missing or out of order";
            } else if (!r.prevHash().equals(prev)) {
                problem = "does not link to the previous entry";
            } else if (!AuditHashing.hash(r.sequence(), r.event(), r.prevHash()).equals(r.hash())) {
                problem = "was modified after it was written";
            }
            if (problem != null) {
                String intact = expected == 1 ? "no earlier entries" : "entries 1-" + (expected - 1) + " are intact";
                return new Checked(new Verification(false, (int) expected - 1,
                        "Audit log TAMPERED: entry " + expected + " " + problem + "; " + intact), from);
            }
            prev = r.hash();
            expected++;
        }
        int n = (int) expected - 1;
        return new Checked(new Verification(true, n,
                "Audit log intact: " + n + " entr" + (n == 1 ? "y" : "ies") + " verified"),
                new ChainPoint(n, prev));
    }
}
