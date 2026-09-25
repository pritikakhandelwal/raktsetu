package com.raktsetu.domain.model;

import java.util.Objects;

/**
 * A hospital's request for blood.
 *
 * @param hospitalId the requesting facility
 * @param component  the component needed
 * @param recipient  the patient's blood group
 * @param quantity   number of units, at least 1
 * @param urgency    how the allocator should search
 */
public record BloodRequest(String hospitalId, ComponentType component, BloodGroup recipient, int quantity,
                           Urgency urgency) {

    public BloodRequest {
        Objects.requireNonNull(hospitalId, "hospitalId");
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(urgency, "urgency");
        if (quantity < 1) {
            throw new IllegalArgumentException("A request needs at least 1 unit, was " + quantity);
        }
    }

    /** An emergency request, the most common case in the console. */
    public BloodRequest(String hospitalId, ComponentType component, BloodGroup recipient, int quantity) {
        this(hospitalId, component, recipient, quantity, Urgency.EMERGENCY);
    }
}
