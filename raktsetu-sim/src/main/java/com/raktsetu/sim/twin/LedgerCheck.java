package com.raktsetu.sim.twin;

import com.raktsetu.domain.model.AuditEvent;
import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.engine.BloodNetwork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Replays the audit trail from an empty network and checks that it explains the final stock exactly: every unit was
 * collected once, moved only from where it was, left at most once (issued or discarded), and every unit still on a
 * shelf is where the replay says it is. The audit trail is effectively the event log of the simulation.
 */
public final class LedgerCheck {

    private LedgerCheck() {
    }

    /** Problems found; an empty list means stock is conserved. */
    public static List<String> verify(List<AuditEvent> events, BloodNetwork finalState) {
        List<String> problems = new ArrayList<>();
        Map<String, String> location = new HashMap<>();
        for (AuditEvent e : events) {
            for (String unit : e.unitIds()) {
                String at = location.get(unit);
                switch (e.type()) {
                    case COLLECTED -> {
                        if (at != null) {
                            problems.add(unit + " collected twice");
                        }
                        location.put(unit, e.facilityId());
                    }
                    case ISSUED, DISCARDED -> {
                        if (!e.facilityId().equals(at)) {
                            problems.add(unit + " " + e.type() + " at " + e.facilityId() + " but it was at " + at);
                        }
                        location.remove(unit);
                    }
                    case TRANSFERRED -> {
                        if (!e.facilityId().equals(at)) {
                            problems.add(unit + " moved from " + e.facilityId() + " but it was at " + at);
                        }
                        location.put(unit, e.counterparty());
                    }
                }
            }
        }
        int onShelves = 0;
        for (Facility f : finalState.storingFacilities()) {
            for (BloodComponent u : f.getInventory().allUnits()) {
                onShelves++;
                if (!f.getId().equals(location.get(u.getId()))) {
                    problems.add(u.getId() + " is at " + f.getId() + " but the log says " + location.get(u.getId()));
                }
            }
        }
        if (onShelves != location.size()) {
            problems.add("The log accounts for " + location.size() + " units in stock but " + onShelves
                    + " are on shelves");
        }
        return problems;
    }
}
