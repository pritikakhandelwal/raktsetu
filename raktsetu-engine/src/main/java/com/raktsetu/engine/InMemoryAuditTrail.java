package com.raktsetu.engine;

import com.raktsetu.domain.interfaces.AuditTrail;
import com.raktsetu.domain.model.AuditEvent;

import java.util.ArrayList;
import java.util.List;

/** An audit trail kept in memory, for the simulator and tests. */
public final class InMemoryAuditTrail implements AuditTrail {

    private final List<AuditEvent> events = new ArrayList<>();

    @Override
    public void record(AuditEvent event) {
        events.add(event);
    }

    public List<AuditEvent> events() {
        return List.copyOf(events);
    }
}
