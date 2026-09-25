package com.raktsetu.persistence;

import com.raktsetu.domain.interfaces.Identifiable;
import com.raktsetu.domain.model.BloodComponent;

import java.util.Objects;

/** A unit together with the facility holding it: one row of {@code units.csv}. */
public record StockedUnit(String facilityId, BloodComponent unit) implements Identifiable {

    public StockedUnit {
        Objects.requireNonNull(facilityId, "facilityId");
        Objects.requireNonNull(unit, "unit");
    }

    @Override
    public String getId() {
        return unit.getId();
    }
}
