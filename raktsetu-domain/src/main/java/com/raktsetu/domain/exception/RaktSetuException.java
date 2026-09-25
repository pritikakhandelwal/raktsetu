package com.raktsetu.domain.exception;

/**
 * Base class for every expected, recoverable problem in RaktSetu.
 *
 * <p>These are checked exceptions on purpose: callers (the console, later the API) must decide how to show each one
 * to staff instead of letting the program crash.
 */
public class RaktSetuException extends Exception {

    private static final long serialVersionUID = 1L;

    public RaktSetuException(String message) {
        super(message);
    }

    public RaktSetuException(String message, Throwable cause) {
        super(message, cause);
    }
}
