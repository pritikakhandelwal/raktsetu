package com.raktsetu.server.alerts;

import com.raktsetu.server.db.NotificationRepository.Kind;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@code alert_outbox} table: alerts waiting to be sent, and the record of those sent or given up on.
 *
 * <p>{@link #enqueue} runs inside the transaction that creates the notification, so an alert exists exactly when
 * its notification does. Sending happens later, in {@link AlertDispatcher}, never while a user's request waits.
 */
@Repository
public class AlertOutbox {

    /** Where an alert is in its life. */
    public enum Status { PENDING, SENT, FAILED }

    /** An alert due to be sent. */
    public record Due(long id, String phone, String channel, String message, int attempts) {
    }

    /** A delivery as administrators see it, with the number masked. */
    public record Delivery(long id, String username, String phone, String channel, String message, Status status,
                           int attempts, String lastError, Instant createdAt, Instant sentAt) {
    }

    static final String PREFIX = "RaktSetu: ";

    private final JdbcClient jdbc;
    private final AlertChannels channels;
    private final Set<Kind> kinds;
    private final Clock clock;

    public AlertOutbox(JdbcClient jdbc, AlertChannels channels, AlertProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.channels = channels;
        this.kinds = properties.alertKinds();
        this.clock = clock;
    }

    /**
     * Queues alerts for a new notification, if its kind is one that alerts: one per person in the audience who has
     * a mobile number and has turned alerts on, per active channel. Returns how many were queued.
     *
     * @param facilityId the audience (that facility's staff), or null for administrators
     */
    public int enqueue(long notificationId, String facilityId, Kind kind, String message) {
        if (!kinds.contains(kind)) {
            return 0;
        }
        String audience = facilityId == null ? "role = 'ADMIN'" : "facility_id = :facility";
        var query = jdbc.sql("select username, phone from app_user where enabled = true and alerts_enabled = true "
                + "and phone is not null and " + audience);
        if (facilityId != null) {
            query = query.param("facility", facilityId);
        }
        List<String[]> recipients = query.query((rs, n) -> new String[] {rs.getString(1), rs.getString(2)}).list();
        int queued = 0;
        for (String[] r : recipients) {
            queued += insert(notificationId, r[0], r[1], message);
        }
        return queued;
    }

    /** Queues a test alert to one person, whatever their alert setting. */
    public int enqueueTest(String username, String phone) {
        return insert(null, username, phone, "Test alert. If you received this, urgent alerts will reach you here.");
    }

    private int insert(Long notificationId, String username, String phone, String message) {
        long now = clock.millis();
        String text = PREFIX + (message.length() > 480 ? message.substring(0, 477) + "..." : message);
        int n = 0;
        for (AlertChannel channel : channels.active()) {
            jdbc.sql("insert into alert_outbox (notification_id, username, phone, channel, message, status, attempts, "
                            + "next_attempt_at, created_at) values (?, ?, ?, ?, ?, ?, 0, ?, ?)")
                    .params(notificationId, username, phone, channel.name(), text, Status.PENDING.name(), now, now)
                    .update();
            n++;
        }
        return n;
    }

    /** Pending alerts whose next attempt is due, oldest first. */
    public List<Due> due(long now, int limit) {
        return jdbc.sql("select id, phone, channel, message, attempts from alert_outbox where status = 'PENDING' "
                        + "and next_attempt_at <= ? order by id limit " + limit)
                .param(now)
                .query((rs, n) -> new Due(rs.getLong("id"), rs.getString("phone"), rs.getString("channel"),
                        rs.getString("message"), rs.getInt("attempts")))
                .list();
    }

    public void markSent(long id, int attempts, long now) {
        jdbc.sql("update alert_outbox set status = 'SENT', attempts = ?, sent_at = ?, last_error = null where id = ?")
                .params(attempts, now, id).update();
    }

    public void markRetry(long id, int attempts, long nextAttemptAt, String error) {
        jdbc.sql("update alert_outbox set attempts = ?, next_attempt_at = ?, last_error = ? where id = ?")
                .params(attempts, nextAttemptAt, trim(error), id).update();
    }

    public void markFailed(long id, int attempts, String error) {
        jdbc.sql("update alert_outbox set status = 'FAILED', attempts = ?, last_error = ? where id = ?")
                .params(attempts, trim(error), id).update();
    }

    /** The newest deliveries, newest first. */
    public List<Delivery> recent(int limit) {
        return jdbc.sql("select * from alert_outbox order by id desc limit " + limit)
                .query(AlertOutbox::delivery).list();
    }

    /**
     * Forgets someone's number in the alert records, for when they withdraw consent or their details are erased:
     * alerts still waiting are given up, and the number is replaced by {@value #REMOVED} in every record. Returns how
     * many records changed.
     */
    public int forget(String username) {
        jdbc.sql("update alert_outbox set status = 'FAILED', last_error = 'Number removed' "
                + "where username = ? and status = 'PENDING'").param(username).update();
        return jdbc.sql("update alert_outbox set phone = ? where username = ?").params(REMOVED, username).update();
    }

    /** What replaces an erased number in the alert records. */
    public static final String REMOVED = "removed";

    /** One person's alerts, newest first, with the number masked: for their own copy of their data. */
    public List<Delivery> forUser(String username) {
        return jdbc.sql("select * from alert_outbox where username = ? order by id desc").param(username)
                .query(AlertOutbox::delivery).list();
    }

    /** Deletes sent and failed alerts created before {@code cutoff}. Pending alerts are kept. */
    public int purgeBefore(long cutoff) {
        return jdbc.sql("delete from alert_outbox where status <> 'PENDING' and created_at < ?").param(cutoff)
                .update();
    }

    private static Delivery delivery(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        long sent = rs.getLong("sent_at");
        boolean notSent = rs.wasNull();
        return new Delivery(rs.getLong("id"), rs.getString("username"), PhoneNumbers.mask(rs.getString("phone")),
                rs.getString("channel"), rs.getString("message"), Status.valueOf(rs.getString("status")),
                rs.getInt("attempts"), rs.getString("last_error"), Instant.ofEpochMilli(rs.getLong("created_at")),
                notSent ? null : Instant.ofEpochMilli(sent));
    }

    /** How many alerts are in each status. */
    public Map<Status, Integer> counts() {
        Map<Status, Integer> counts = new LinkedHashMap<>();
        for (Status s : Status.values()) {
            counts.put(s, 0);
        }
        jdbc.sql("select status, count(*) from alert_outbox group by status")
                .query((rs, n) -> counts.put(Status.valueOf(rs.getString(1)), rs.getInt(2))).list();
        return counts;
    }

    private static String trim(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 300 ? error.substring(0, 297) + "..." : error;
    }
}
