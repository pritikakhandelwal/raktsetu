package com.raktsetu.server.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Decides whether a sign-in may be tried for a key (a username or an IP address), based on the failures recorded
 * for it. Implementations must be safe to call from many request threads at once.
 */
public interface LoginAttemptPolicy {

    /** How long the key must wait before trying again, or empty if a sign-in may be tried now. */
    Optional<Duration> lockedFor(String key, Instant now);

    /** Records a failed sign-in for the key. */
    void recordFailure(String key, Instant now);

    /** Forgets the failures for the key, after a successful sign-in or a password reset. */
    void reset(String key);
}
