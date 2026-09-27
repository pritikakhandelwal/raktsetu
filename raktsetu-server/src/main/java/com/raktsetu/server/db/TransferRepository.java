package com.raktsetu.server.db;

import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.TransferOrder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Transfer plans and what happened to each transfer. */
@Repository
public class TransferRepository {

    /** Where a transfer is in its life. */
    public enum Status {
        PROPOSED, APPROVED, RECEIVED, REJECTED, SUPERSEDED
    }

    public record Row(String id, String sourceId, String destinationId, String units, int unitCount, int distanceKm,
                      int travelMinutes, Status status, String reason, LocalDate plannedOn, String decidedBy,
                      LocalDate decidedOn, LocalDate receivedOn) {
    }

    private final JdbcClient jdbc;

    public TransferRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Marks every still-proposed transfer as replaced by a newer plan. */
    public void supersedeProposed() {
        jdbc.sql("update transfer set status = ? where status = ?")
                .params(Status.SUPERSEDED.name(), Status.PROPOSED.name()).update();
    }

    /** Stores a proposed transfer. IDs are made unique by suffixing a letter if the same ID was used earlier. */
    public String insert(TransferOrder o) {
        String id = o.getId();
        for (char c = 'b'; exists(id); c++) {
            id = o.getId() + c;
        }
        jdbc.sql("insert into transfer (id, source_id, destination_id, units_desc, unit_count, distance_km, "
                        + "travel_minutes, status, reason, planned_on) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(id, o.getSource().getId(), o.getDestination().getId(), o.describeUnits(), o.unitCount(),
                        o.getRoute().distanceKm(), o.getRoute().travelMinutes(), Status.PROPOSED.name(),
                        o.getReason().length() > 1000 ? o.getReason().substring(0, 997) + "..." : o.getReason(),
                        o.getPlannedOn())
                .update();
        for (BloodComponent u : o.getUnits()) {
            jdbc.sql("insert into transfer_unit (transfer_id, unit_id) values (?, ?)").params(id, u.getId()).update();
        }
        return id;
    }

    private boolean exists(String id) {
        return jdbc.sql("select count(*) from transfer where id = ?").param(id).query(Integer.class).single() > 0;
    }

    public int countProposed() {
        return jdbc.sql("select count(*) from transfer where status = ?").param(Status.PROPOSED.name())
                .query(Integer.class).single();
    }

    public Optional<Row> find(String id) {
        return jdbc.sql("select * from transfer where id = ?").param(id).query(TransferRepository::row).optional();
    }

    public List<String> unitIds(String id) {
        return jdbc.sql("select unit_id from transfer_unit where transfer_id = ? order by unit_id").param(id)
                .query(String.class).list();
    }

    /** Current and recent transfers: proposed first, then approved but not yet received, then the latest others. */
    public List<Row> current(int limit) {
        return jdbc.sql("select * from transfer where status <> ? order by case status when 'PROPOSED' then 0 "
                        + "when 'APPROVED' then 1 else 2 end, planned_on desc, id limit " + limit)
                .param(Status.SUPERSEDED.name()).query(TransferRepository::row).list();
    }

    public void decide(String id, Status status, String by, LocalDate day) {
        jdbc.sql("update transfer set status = ?, decided_by = ?, decided_on = ? where id = ?")
                .params(status.name(), by, day, id).update();
    }

    public void markReceived(String id, LocalDate day) {
        jdbc.sql("update transfer set status = ?, received_on = ? where id = ?")
                .params(Status.RECEIVED.name(), day, id).update();
    }

    private static Row row(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Row(rs.getString("id"), rs.getString("source_id"), rs.getString("destination_id"),
                rs.getString("units_desc"), rs.getInt("unit_count"), rs.getInt("distance_km"),
                rs.getInt("travel_minutes"), Status.valueOf(rs.getString("status")), rs.getString("reason"),
                rs.getObject("planned_on", LocalDate.class), rs.getString("decided_by"),
                rs.getObject("decided_on", LocalDate.class), rs.getObject("received_on", LocalDate.class));
    }
}
