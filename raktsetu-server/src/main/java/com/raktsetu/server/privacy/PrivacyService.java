package com.raktsetu.server.privacy;

import com.raktsetu.server.alerts.AlertOutbox;
import com.raktsetu.server.security.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The data protection duties RaktSetu carries out itself: telling people what is held (the privacy notice), giving
 * each person a copy of their own data, and deleting alert records once they are no longer needed.
 *
 * <p>Patients are never identified: a blood request holds only the component, blood group and quantity. The personal
 * data held is about staff: their name, username, role, workplace and, if they choose, a mobile number for alerts.
 */
@Service
public class PrivacyService {

    /** The privacy notice's facts, shown to anyone before they sign in. */
    public record Notice(String organisation, String contact, String noticeVersion, int alertRetentionDays) {
    }

    /** Everything RaktSetu holds about one person, for their own copy. */
    public record MyData(Instant generatedAt, String username, String displayName, String role, String facilityId,
                         LocalDate accountCreatedOn, boolean enabled, String phone, boolean alertsEnabled,
                         String phoneHeldBecause, Instant phoneRecordedAt, String noticeVersion,
                         int requestsRaised, int transfersDecided, List<AlertOutbox.Delivery> alerts, String notes) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(PrivacyService.class);

    private final PrivacyProperties properties;
    private final UserRepository users;
    private final AlertOutbox outbox;
    private final JdbcClient jdbc;
    private final Clock clock;

    public PrivacyService(PrivacyProperties properties, UserRepository users, AlertOutbox outbox, JdbcClient jdbc,
                          Clock clock) {
        this.properties = properties;
        this.users = users;
        this.outbox = outbox;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Notice notice() {
        return new Notice(properties.organisation(), properties.contact(), properties.noticeVersion(),
                properties.alertRetentionDays());
    }

    /** A copy of everything held about {@code username}. */
    public MyData myData(String username) {
        UserRepository.Account account = users.find(username)
                .orElseThrow(() -> new IllegalArgumentException("No account '" + username + "'"));
        UserRepository.ContactRecord contact = users.contactRecord(username).orElseThrow();
        int requests = jdbc.sql("select count(*) from blood_request where raised_by = ?").param(username)
                .query(Integer.class).single();
        int transfers = jdbc.sql("select count(*) from transfer where decided_by = ?").param(username)
                .query(Integer.class).single();
        String because = contact.basis() == null ? null : switch (contact.basis()) {
            case "CONSENT" -> "You turned on urgent alerts and agreed to the privacy notice";
            case "EMPLOYER" -> "An administrator added it for work alerts when creating your account";
            default -> "Saved before the reason was recorded";
        };
        return new MyData(clock.instant(), username, account.user().displayName(), account.user().role().name(),
                account.user().facilityId(), account.createdOn(), account.enabled(), account.phone(),
                account.alertsEnabled(), because,
                contact.recordedAt() == null ? null : Instant.ofEpochMilli(contact.recordedAt()),
                contact.noticeVersion(), requests, transfers, outbox.forUser(username),
                "Your username also appears on the requests you raised and the transfers you decided, which are kept "
                        + "as the network's record of blood movements. Your password is stored only as a one-way "
                        + "hash and is not included.");
    }

    /** Deletes sent and failed alerts older than the retention period. Runs daily at 02:00. */
    @Scheduled(cron = "0 0 2 * * *", zone = "${raktsetu.time-zone:Asia/Kolkata}")
    public int purgeOldAlerts() {
        long cutoff = clock.instant().minus(Duration.ofDays(properties.alertRetentionDays())).toEpochMilli();
        int deleted = outbox.purgeBefore(cutoff);
        if (deleted > 0) {
            LOG.info("Deleted {} alert records older than {} days", deleted, properties.alertRetentionDays());
        }
        return deleted;
    }
}
