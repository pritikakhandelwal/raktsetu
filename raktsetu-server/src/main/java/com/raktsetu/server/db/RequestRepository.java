package com.raktsetu.server.db;

import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.BloodRequest;
import com.raktsetu.domain.model.ComponentType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Hospital requests and the units allocated to them. */
@Repository
public class RequestRepository {

    /** Where a request is in its life. */
    public enum Status {
        ALLOCATED, DISPATCHED, RECEIVED, CANCELLED, UNMET
    }

    public record Row(long id, String hospitalId, ComponentType component, BloodGroup group, int quantity,
                      String urgency, Status status, LocalDate raisedOn, String raisedBy, LocalDate updatedOn,
                      String note) {
    }

    public record UnitRow(long requestId, String unitId, String sourceId, ComponentType component, BloodGroup group,
                          LocalDate collectedOn, LocalDate expiresOn, boolean dispatched) {

        /** The unit as a stock object again, for putting it back on the shelf. */
        public BloodComponent toUnit() {
            BloodComponent u = component.newUnit(unitId, group, collectedOn);
            return u.getExpiryDate().equals(expiresOn) ? u : component.newUnit(unitId, group, collectedOn, expiresOn);
        }
    }

    private final JdbcClient jdbc;

    public RequestRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Stores a new request and returns its number. */
    public long insert(BloodRequest r, Status status, LocalDate day, String raisedBy, String note) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.sql("insert into blood_request (hospital_id, component, blood_group, quantity, urgency, status, "
                        + "raised_on, raised_by, updated_on, note) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(r.hospitalId(), r.component().name(), r.recipient().label(), r.quantity(), r.urgency().name(),
                        status.name(), day, raisedBy, day, note)
                .update(key, "id");
        return key.getKeyAs(Number.class).longValue();
    }

    public void addUnit(long requestId, String sourceId, BloodComponent u) {
        jdbc.sql("insert into request_unit (request_id, unit_id, source_id, component, blood_group, collected_on, "
                        + "expires_on, dispatched) values (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(requestId, u.getId(), sourceId, u.getType().name(), u.getBloodGroup().label(),
                        u.getCollectedOn(), u.getExpiryDate(), false)
                .update();
    }

    public Optional<Row> find(long id) {
        return jdbc.sql("select * from blood_request where id = ?").param(id).query(RequestRepository::row).optional();
    }

    public List<UnitRow> units(long requestId) {
        return jdbc.sql("select * from request_unit where request_id = ? order by source_id, unit_id").param(requestId)
                .query((rs, n) -> new UnitRow(rs.getLong("request_id"), rs.getString("unit_id"),
                        rs.getString("source_id"), ComponentType.valueOf(rs.getString("component")),
                        BloodGroup.parse(rs.getString("blood_group")), rs.getObject("collected_on", LocalDate.class),
                        rs.getObject("expires_on", LocalDate.class), rs.getBoolean("dispatched")))
                .list();
    }

    /** Newest first. {@code hospitalId} and {@code sourceId} narrow the list; both null lists everything. */
    public List<Row> latest(String hospitalId, String sourceId, int limit) {
        if (hospitalId != null) {
            return jdbc.sql("select * from blood_request where hospital_id = ? order by id desc limit " + limit)
                    .param(hospitalId).query(RequestRepository::row).list();
        }
        if (sourceId != null) {
            return jdbc.sql("select * from blood_request where id in "
                            + "(select request_id from request_unit where source_id = ?) order by id desc limit " + limit)
                    .param(sourceId).query(RequestRepository::row).list();
        }
        return jdbc.sql("select * from blood_request order by id desc limit " + limit)
                .query(RequestRepository::row).list();
    }

    public void setStatus(long id, Status status, LocalDate day) {
        jdbc.sql("update blood_request set status = ?, updated_on = ? where id = ?").params(status.name(), day, id)
                .update();
    }

    public void markDispatched(long requestId, String sourceId) {
        jdbc.sql("update request_unit set dispatched = true where request_id = ? and source_id = ?")
                .params(requestId, sourceId).update();
    }

    private static Row row(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Row(rs.getLong("id"), rs.getString("hospital_id"), ComponentType.valueOf(rs.getString("component")),
                BloodGroup.parse(rs.getString("blood_group")), rs.getInt("quantity"), rs.getString("urgency"),
                Status.valueOf(rs.getString("status")), rs.getObject("raised_on", LocalDate.class),
                rs.getString("raised_by"), rs.getObject("updated_on", LocalDate.class), rs.getString("note"));
    }
}
