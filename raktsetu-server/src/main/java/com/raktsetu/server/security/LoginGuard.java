package com.raktsetu.server.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Slows down password guessing. Two policies apply to every sign-in: one per username (5 failures in 15 minutes
 * locks that account) and one per client address (20 failures in 15 minutes locks that address, which stops one
 * machine trying many usernames). An administrator's password reset clears the account's lock.
 */
@Component
public class LoginGuard {

    private final LoginAttemptPolicy perUser;
    private final LoginAttemptPolicy perAddress;
    private final Clock clock;
    private final Counter failed;
    private final Counter refused;

    @Autowired
    public LoginGuard(Clock clock, MeterRegistry meters) {
        this(new SlidingWindowAttemptPolicy(5, Duration.ofMinutes(15)),
                new SlidingWindowAttemptPolicy(20, Duration.ofMinutes(15)), clock, meters);
    }

    LoginGuard(LoginAttemptPolicy perUser, LoginAttemptPolicy perAddress, Clock clock, MeterRegistry meters) {
        this.perUser = perUser;
        this.perAddress = perAddress;
        this.clock = clock;
        this.failed = Counter.builder("raktsetu.logins.failed").description("Sign-ins with a wrong password")
                .register(meters);
        this.refused = Counter.builder("raktsetu.logins.locked").description("Sign-ins refused by the lockout")
                .register(meters);
    }

    /** Throws {@link TooManyAttemptsException} if either the username or the address is locked. */
    public void check(String username, String address) {
        var now = clock.instant();
        Optional<Duration> wait = List.of(perUser.lockedFor(userKey(username), now),
                        perAddress.lockedFor(address, now)).stream()
                .flatMap(Optional::stream)
                .max(Comparator.naturalOrder());
        if (wait.isPresent()) {
            refused.increment();
            throw new TooManyAttemptsException(wait.get());
        }
    }

    public void failed(String username, String address) {
        var now = clock.instant();
        perUser.recordFailure(userKey(username), now);
        perAddress.recordFailure(address, now);
        failed.increment();
    }

    public void succeeded(String username) {
        perUser.reset(userKey(username));
    }

    /** After an administrator resets someone's password, they can sign in straight away. */
    public void clear(String username) {
        perUser.reset(userKey(username));
    }

    private static String userKey(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
