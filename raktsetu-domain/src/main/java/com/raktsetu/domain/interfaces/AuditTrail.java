package com.raktsetu.domain.interfaces;

import com.raktsetu.domain.model.AuditEvent;

/**
 * Where every collection, issue, transfer and discard is recorded.
 *
 * <p>The engine only knows this interface. The console uses a hash-chained file; the simulator and tests use an
 * in-memory list.
 */
public interface AuditTrail {

    void record(AuditEvent event);
}
