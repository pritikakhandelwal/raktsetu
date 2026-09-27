package com.raktsetu.server;

import com.raktsetu.domain.interfaces.AuditTrail;
import com.raktsetu.domain.model.AuditEvent;

import java.util.ArrayList;
import java.util.List;

/** Collects the audit events of one command so they can be written to the database together. */
final class CapturingAuditTrail implements AuditTrail {

    private final List<AuditEvent> events = new ArrayList<>();

    @Override
    public void record(AuditEvent event) {
        events.add(event);
    }

    void clear() {
        events.clear();
    }

    /** The captured events, which are then cleared. */
    List<AuditEvent> drain() {
        List<AuditEvent> copy = List.copyOf(events);
        events.clear();
        return copy;
    }
}
