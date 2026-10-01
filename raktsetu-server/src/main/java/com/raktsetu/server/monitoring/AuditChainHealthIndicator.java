package com.raktsetu.server.monitoring;

import com.raktsetu.server.db.AuditRepository;
import com.raktsetu.server.db.AuditRepository.Verification;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports the service as DOWN if the hash-chained audit log has been tampered with, so a monitoring system raises
 * an alarm. Shown under {@code /actuator/health} as {@code auditChain}. Verifying reads the whole log, so the result
 * is kept for a minute between checks.
 */
@Component("auditChain")
public class AuditChainHealthIndicator implements HealthIndicator {

    static final Duration CACHE_FOR = Duration.ofMinutes(1);

    private final AuditRepository audit;
    private final Clock clock;
    private Verification last;
    private Instant checkedAt = Instant.MIN;

    public AuditChainHealthIndicator(AuditRepository audit, Clock clock) {
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public synchronized Health health() {
        Instant now = clock.instant();
        if (last == null || now.isAfter(checkedAt.plus(CACHE_FOR))) {
            last = audit.verify();
            checkedAt = now;
        }
        Health.Builder health = last.intact() ? Health.up() : Health.down();
        return health.withDetail("entries", last.entries())
                .withDetail("message", last.message())
                .withDetail("checkedAt", checkedAt.toString())
                .build();
    }
}
