package com.raktsetu.domain.model;

import com.raktsetu.domain.interfaces.Exportable;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** A unit removed from stock because it expired. */
public record WastageRecord(LocalDate date, String facilityId, String unitId, ComponentType component,
                            BloodGroup group) implements Exportable {

    public WastageRecord {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(facilityId, "facilityId");
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(group, "group");
    }

    public static WastageRecord of(LocalDate date, String facilityId, BloodComponent unit) {
        return new WastageRecord(date, facilityId, unit.getId(), unit.getType(), unit.getBloodGroup());
    }

    @Override
    public List<String> toCsvRow() {
        return List.of(date.toString(), facilityId, unitId, component.name(), group.label());
    }
}
