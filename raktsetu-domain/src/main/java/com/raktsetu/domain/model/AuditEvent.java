package com.raktsetu.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One thing that happened to one or more units.
 *
 * @param date         the business date ("today" in the app or simulator)
 * @param type         what happened
 * @param facilityId   where it happened (the source, for transfers)
 * @param counterparty the receiving hospital or destination, or an empty string
 * @param component    component of the units
 * @param group        blood group of the units
 * @param unitIds      the units involved
 * @param note         free-text explanation, for example a substitution reason
 */
public record AuditEvent(LocalDate date, AuditEventType type, String facilityId, String counterparty,
                         ComponentType component, BloodGroup group, List<String> unitIds, String note) {

    public AuditEvent {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(facilityId, "facilityId");
        counterparty = counterparty == null ? "" : counterparty;
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(group, "group");
        unitIds = List.copyOf(unitIds);
        if (unitIds.isEmpty()) {
            throw new IllegalArgumentException("An audit event must involve at least one unit");
        }
        note = note == null ? "" : note;
    }
}
