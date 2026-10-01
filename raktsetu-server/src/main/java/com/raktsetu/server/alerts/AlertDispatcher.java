package com.raktsetu.server.alerts;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Sends queued alerts. Runs every few seconds on its own thread, outside the network lock and outside any user's
 * request, so a slow or failing provider never slows down the site.
 *
 * <p>A failure that may pass (provider down, rate limit, network error) is retried with exponential back-off: 30 s,
 * 1 min, 2 min, 4 min... up to {@code maxAttempts} tries. A failure retrying cannot fix (a wrong number, a rejected
 * key) is marked failed at once. Administrators see both on the People page.
 */
@Component
public class AlertDispatcher {

    /** What one pass did. */
    public record Report(int sent, int retrying, int failed) {
    }

    static final Duration FIRST_RETRY = Duration.ofSeconds(30);
    static final Duration LONGEST_WAIT = Duration.ofHours(1);
    private static final int BATCH = 50;
    private static final Logger LOG = LoggerFactory.getLogger(AlertDispatcher.class);

    private final AlertOutbox outbox;
    private final AlertChannels channels;
    private final int maxAttempts;
    private final Clock clock;
    private final Counter sentCounter;
    private final Counter retryCounter;
    private final Counter failedCounter;

    public AlertDispatcher(AlertOutbox outbox, AlertChannels channels, AlertProperties properties, Clock clock,
                           MeterRegistry meters) {
        this.outbox = outbox;
        this.channels = channels;
        this.maxAttempts = properties.maxAttempts();
        this.clock = clock;
        this.sentCounter = Counter.builder("raktsetu.alerts.sent").description("Alerts delivered").register(meters);
        this.retryCounter = Counter.builder("raktsetu.alerts.retried").description("Alert attempts to retry later")
                .register(meters);
        this.failedCounter = Counter.builder("raktsetu.alerts.failed").description("Alerts given up on")
                .register(meters);
    }

    @Scheduled(fixedDelayString = "${raktsetu.alerts.poll-ms:5000}", initialDelayString = "${raktsetu.alerts.poll-ms:5000}")
    public void run() {
        deliverDue(clock.millis());
    }

    /** Sends everything due at {@code now}. One pass at a time. */
    public synchronized Report deliverDue(long now) {
        int sent = 0;
        int retrying = 0;
        int failed = 0;
        for (AlertOutbox.Due due : outbox.due(now, BATCH)) {
            int attempt = due.attempts() + 1;
            Optional<AlertChannel> channel = channels.byName(due.channel());
            try {
                if (channel.isEmpty()) {
                    throw new AlertDeliveryException("Unknown channel " + due.channel(), false);
                }
                channel.get().send(due.phone(), due.message());
                outbox.markSent(due.id(), attempt, now);
                sentCounter.increment();
                sent++;
            } catch (AlertDeliveryException e) {
                if (e.retryable() && attempt < maxAttempts) {
                    outbox.markRetry(due.id(), attempt, now + backoff(attempt).toMillis(), e.getMessage());
                    retryCounter.increment();
                    retrying++;
                } else {
                    outbox.markFailed(due.id(), attempt, e.getMessage());
                    failedCounter.increment();
                    failed++;
                    LOG.warn("Alert {} to {} by {} failed after {} attempt(s): {}", due.id(),
                            PhoneNumbers.mask(due.phone()), due.channel(), attempt, e.getMessage());
                }
            }
        }
        return new Report(sent, retrying, failed);
    }

    /** Wait before try {@code attempt + 1}: 30 s, 60 s, 120 s ... never more than an hour. */
    static Duration backoff(int attempt) {
        Duration wait = FIRST_RETRY.multipliedBy(1L << Math.min(attempt - 1, 20));
        return wait.compareTo(LONGEST_WAIT) > 0 ? LONGEST_WAIT : wait;
    }
}
