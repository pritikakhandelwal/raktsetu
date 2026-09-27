package com.raktsetu.server.alerts;

import com.raktsetu.server.alerts.AlertOutbox.Delivery;
import com.raktsetu.server.alerts.AlertOutbox.Status;
import com.raktsetu.server.security.AppUser;
import com.raktsetu.server.security.CurrentUsers;
import com.raktsetu.server.security.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Alert delivery status for administrators, and a test alert anyone can send to their own phone. */
@RestController
@Tag(name = "Alerts", description = "SMS and WhatsApp alerts: channels, delivery status and a test message")
@RequestMapping("/api/alerts")
public class AlertController {

    /** What administrators see. */
    public record AlertsView(List<AlertChannels.Status> channels, Map<Status, Integer> counts,
                             List<Delivery> recent) {
    }

    /** At most one test alert per person per minute, so nobody can spend the SMS budget by clicking. */
    static final Duration TEST_INTERVAL = Duration.ofMinutes(1);

    private final AlertChannels channels;
    private final AlertOutbox outbox;
    private final UserRepository users;
    private final CurrentUsers current;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Map<String, Instant> lastTest = new ConcurrentHashMap<>();

    public AlertController(AlertChannels channels, AlertOutbox outbox, UserRepository users, CurrentUsers current,
                           TransactionTemplate tx, Clock clock) {
        this.channels = channels;
        this.outbox = outbox;
        this.users = users;
        this.current = current;
        this.tx = tx;
        this.clock = clock;
    }

    @GetMapping
    public AlertsView status(Authentication auth) {
        current.of(auth).requireAdmin();
        return new AlertsView(channels.status(), outbox.counts(), outbox.recent(30));
    }

    /** Queues a test alert to the signed-in person's own number; it goes out within a few seconds. */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> test(Authentication auth) {
        AppUser me = current.of(auth);
        String phone = users.find(me.username()).map(UserRepository.Account::phone).orElse(null);
        if (phone == null) {
            throw new IllegalArgumentException("Add your mobile number first");
        }
        Instant now = clock.instant();
        Instant previous = lastTest.get(me.username());
        if (previous != null && now.isBefore(previous.plus(TEST_INTERVAL))) {
            throw new IllegalArgumentException("A test alert was sent less than a minute ago; please wait");
        }
        lastTest.put(me.username(), now);
        Integer queued = tx.execute(s -> outbox.enqueueTest(me.username(), phone));
        List<String> via = channels.active().stream().map(AlertChannel::name).toList();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("queued", queued == null ? 0 : queued,
                "to", PhoneNumbers.mask(phone), "channels", via));
    }
}
