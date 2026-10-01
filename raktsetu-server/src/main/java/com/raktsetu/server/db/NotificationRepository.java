package com.raktsetu.server.db;

import com.raktsetu.server.alerts.AlertOutbox;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * In-app notifications. Each is addressed to one facility's staff, or to administrators ({@code facilityId} null);
 * everyone in that audience sees it and any of them can mark it read.
 */
@Repository
public class NotificationRepository {

    /** Kinds of notification, for icons and filtering in the app. */
    public enum Kind {
        REQUEST_TO_DISPATCH, REQUEST_DISPATCHED, REQUEST_CANCELLED, REQUEST_UNMET, TRANSFER_OUTGOING,
        TRANSFER_INCOMING, EXPIRING_SOON, REPORT_OVERDUE
    }

    public record Notification(long id, LocalDate createdOn, String facilityId, String kind, String message,
                               boolean read) {
    }

    private final JdbcClient jdbc;
    private final AlertOutbox alerts;

    public NotificationRepository(JdbcClient jdbc, AlertOutbox alerts) {
        this.jdbc = jdbc;
        this.alerts = alerts;
    }

    /**
     * Adds a notification and, for urgent kinds, queues SMS or WhatsApp alerts to the people in its audience who
     * asked for them. Call inside the transaction of the change it reports, so both happen or neither does.
     *
     * @param facilityId the audience, or null for administrators
     */
    public void add(LocalDate day, String facilityId, Kind kind, String message) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.sql("insert into notification (created_on, facility_id, kind, message, is_read) values (?, ?, ?, ?, ?)")
                .params(day, facilityId, kind.name(), message.length() > 500 ? message.substring(0, 497) + "..."
                        : message, false)
                .update(key, "id");
        alerts.enqueue(key.getKeyAs(Number.class).longValue(), facilityId, kind, message);
    }

    /** The newest notifications for an audience (null = administrators), newest first. */
    public List<Notification> latest(String facilityId, int limit) {
        String where = facilityId == null ? "facility_id is null" : "facility_id = ?";
        var sql = jdbc.sql("select * from notification where " + where + " order by id desc limit " + limit);
        if (facilityId != null) {
            sql = sql.param(facilityId);
        }
        return sql.query((rs, n) -> new Notification(rs.getLong("id"), rs.getObject("created_on", LocalDate.class),
                rs.getString("facility_id"), rs.getString("kind"), rs.getString("message"),
                rs.getBoolean("is_read"))).list();
    }

    public int unread(String facilityId) {
        String where = facilityId == null ? "facility_id is null" : "facility_id = ?";
        var sql = jdbc.sql("select count(*) from notification where is_read = false and " + where);
        if (facilityId != null) {
            sql = sql.param(facilityId);
        }
        return sql.query(Integer.class).single();
    }

    /** Marks one notification read, if it belongs to the audience; returns whether it did. */
    public boolean markRead(long id, String facilityId) {
        String where = facilityId == null ? "facility_id is null" : "facility_id = ?";
        var sql = jdbc.sql("update notification set is_read = true where id = ? and " + where).param(id);
        if (facilityId != null) {
            sql = sql.param(facilityId);
        }
        return sql.update() > 0;
    }

    public void markAllRead(String facilityId) {
        String where = facilityId == null ? "facility_id is null" : "facility_id = ?";
        var sql = jdbc.sql("update notification set is_read = true where " + where);
        if (facilityId != null) {
            sql = sql.param(facilityId);
        }
        sql.update();
    }
}
