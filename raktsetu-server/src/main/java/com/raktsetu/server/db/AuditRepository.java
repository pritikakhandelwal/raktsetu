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

    /** The newest {@code limit} entries, newest first. */
    public List<AuditRecord> latest(int limit) {
        return jdbc.sql("select * from audit_log order by seq desc limit ?").param(Math.max(1, limit))
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
        List<AuditRecord> all = jdbc.sql("select * from audit_log order by seq").query((rs, n) -> new AuditRecord(
                rs.getLong("seq"), new AuditEvent(rs.getObject("event_day", LocalDate.class),
                AuditEventType.valueOf(rs.getString("type")), rs.getString("facility_id"),
                rs.getString("counterparty"), ComponentType.valueOf(rs.getString("component")),
                BloodGroup.parse(rs.getString("blood_group")), Arrays.asList(rs.getString("unit_ids").split(";")),
                rs.getString("note")), rs.getString("prev_hash"), rs.getString("hash"))).list();
        String prev = AuditHashing.GENESIS;
        long expected = 1;
        for (AuditRecord r : all) {
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
                return new Verification(false, (int) expected - 1,
                        "Audit log TAMPERED: entry " + expected + " " + problem + "; " + intact);
            }
            prev = r.hash();
            expected++;
        }
        int n = all.size();
        return new Verification(true, n, "Audit log intact: " + n + " entr" + (n == 1 ? "y" : "ies") + " verified");
    }
}
