package com.raktsetu.domain.exception;

import java.time.LocalDate;

/** An expired unit was about to be issued or transferred. */
public class UnitExpiredException extends RaktSetuException {

    private static final long serialVersionUID = 1L;

    public UnitExpiredException(String unitId, LocalDate expiredOn) {
        super("Unit " + unitId + " expired on " + expiredOn + " and must be discarded, not issued");
    }
}
