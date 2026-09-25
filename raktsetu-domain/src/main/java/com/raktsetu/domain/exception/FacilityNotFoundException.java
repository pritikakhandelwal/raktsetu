package com.raktsetu.domain.exception;

/** An unknown facility ID was entered or referenced. */
public class FacilityNotFoundException extends RaktSetuException {

    private static final long serialVersionUID = 1L;

    public FacilityNotFoundException(String facilityId) {
        super("No facility with ID '" + facilityId + "'");
    }
}
