package com.raktsetu.server.security;

import java.time.Duration;

/** Sign-in refused because there were too many recent failures for this account or address. */
public class TooManyAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyAttemptsException(Duration retryAfter) {
        super("Too many failed sign-ins. Try again in " + minutes(retryAfter) + ", or ask an administrator to reset "
                + "your password.");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    private static String minutes(Duration d) {
        long m = Math.max(1, (d.toSeconds() + 59) / 60);
        return m + (m == 1 ? " minute" : " minutes");
    }
}
