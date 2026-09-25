package com.raktsetu.domain.interfaces;

import com.raktsetu.domain.exception.FacilityNotFoundException;

/**
 * Travel between two facilities.
 *
 * <p>Today this is backed by a precomputed table (see {@code data/README.md}); a live routing service can be added
 * later as another implementation without changing any caller.
 */
public interface DistanceProvider {

    int distanceKm(String fromFacilityId, String toFacilityId) throws FacilityNotFoundException;

    int travelMinutes(String fromFacilityId, String toFacilityId) throws FacilityNotFoundException;
}
