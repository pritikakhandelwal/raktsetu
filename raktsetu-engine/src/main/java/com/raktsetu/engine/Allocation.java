package com.raktsetu.engine;

import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.CompatibilityResult;
import com.raktsetu.domain.model.Compatibility;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.Route;

import java.util.List;

/**
 * Units issued from one facility for one request.
 *
 * @param source        where the units came from
 * @param route         travel from the source to the requesting hospital
 * @param donorGroup    blood group of the issued units
 * @param units         the units, soonest-expiring first
 * @param compatibility why these units are safe for the recipient
 */
public record Allocation(Facility source, Route route, BloodGroup donorGroup, List<BloodComponent> units,
                         CompatibilityResult compatibility) {

    public Allocation {
        units = List.copyOf(units);
    }

    /** True if the units are not the recipient's own group. */
    public boolean isSubstitute() {
        return compatibility.level() != Compatibility.IDENTICAL;
    }
}
