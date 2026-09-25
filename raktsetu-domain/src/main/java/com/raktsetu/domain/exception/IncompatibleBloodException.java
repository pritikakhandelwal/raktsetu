package com.raktsetu.domain.exception;

/** Something tried to issue or transfer a unit that is unsafe for the recipient. */
public class IncompatibleBloodException extends RaktSetuException {

    private static final long serialVersionUID = 1L;

    public IncompatibleBloodException(String reason) {
        super("Incompatible transfusion blocked: " + reason);
    }
}
