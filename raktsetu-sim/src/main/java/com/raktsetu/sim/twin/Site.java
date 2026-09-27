package com.raktsetu.sim.twin;

import com.raktsetu.domain.model.FacilityType;

import java.util.Objects;

/**
 * One facility in a simulated network.
 *
 * @param id           facility ID
 * @param name         display name
 * @param type         hub, storage centre or hospital
 * @param latitude     degrees north (used to compute road routes)
 * @param longitude    degrees east
 * @param demandWeight relative daily demand (hospitals); 1.0 is a typical city hospital
 * @param supplyWeight share of the network's daily collections pushed to this centre (hubs and storage centres)
 * @param rural        true for rural facilities, so fairness between rural and urban hospitals can be measured
 */
public record Site(String id, String name, FacilityType type, double latitude, double longitude, double demandWeight,
                   double supplyWeight, boolean rural) {

    public Site {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (demandWeight < 0 || supplyWeight < 0) {
            throw new IllegalArgumentException("Weights cannot be negative for " + id);
        }
    }

    public boolean isHospital() {
        return type == FacilityType.HOSPITAL;
    }
}
