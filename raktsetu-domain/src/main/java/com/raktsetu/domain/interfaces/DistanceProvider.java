package com.raktsetu.domain.interfaces;

import com.raktsetu.domain.model.Route;

import java.util.Optional;

/**
 * Travel between two facilities.
 *
 * <p>Today this is backed by a precomputed table ({@code TableDistanceProvider}); a live routing service can be added
 * later as another implementation without changing any caller.
 */
public interface DistanceProvider {

    /**
     * The road route between two facilities: {@link Route#ZERO} from a facility to itself, and empty if no route is
     * known.
     */
    Optional<Route> route(String fromFacilityId, String toFacilityId);
}
