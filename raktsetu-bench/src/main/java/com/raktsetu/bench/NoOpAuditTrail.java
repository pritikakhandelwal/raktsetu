package com.raktsetu.bench;

import com.raktsetu.domain.interfaces.AuditTrail;
import com.raktsetu.domain.model.AuditEvent;

/**
 * Discards audit events, so a benchmark measures the engine and not an ever-growing list. Another implementation of
 * the engine's {@link AuditTrail} interface, next to the hash-chained file and the database.
 */
public final class NoOpAuditTrail implements AuditTrail {

    @Override
    public void record(AuditEvent event) {
        // Intentionally empty.
    }
}
