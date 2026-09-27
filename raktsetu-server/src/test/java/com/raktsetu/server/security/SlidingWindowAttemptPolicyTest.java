package com.raktsetu.server.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlidingWindowAttemptPolicyTest {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
    private final SlidingWindowAttemptPolicy policy = new SlidingWindowAttemptPolicy(3, Duration.ofMinutes(10));

    private void fail(String key, int minutesAfterT0) {
        policy.recordFailure(key, T0.plus(Duration.ofMinutes(minutesAfterT0)));
    }

    @Test
    void allowsUntilTheLimitIsReached() {
        fail("asha", 0);
        fail("asha", 1);
        assertThat(policy.lockedFor("asha", T0.plusSeconds(90))).isEmpty();
        fail("asha", 2);
        assertThat(policy.lockedFor("asha", T0.plus(Duration.ofMinutes(2)))).contains(Duration.ofMinutes(8));
    }

    @Test
    void theLockClearsAsFailuresLeaveTheWindow() {
        fail("asha", 0);
        fail("asha", 4);
        fail("asha", 5);
        assertThat(policy.lockedFor("asha", T0.plus(Duration.ofMinutes(9)))).contains(Duration.ofMinutes(1));
        // At 10 minutes the first failure has aged out: two recent failures are below the limit.
        assertThat(policy.lockedFor("asha", T0.plus(Duration.ofMinutes(10)))).isEmpty();
    }

    @Test
    void keysAreIndependentAndResetForgetsFailures() {
        fail("asha", 0);
        fail("asha", 0);
        fail("asha", 0);
        assertThat(policy.lockedFor("ravi", T0)).isEmpty();
        assertThat(policy.lockedFor("asha", T0)).isPresent();
        assertThat(policy.trackedKeys()).isEqualTo(1);
        policy.reset("asha");
        assertThat(policy.lockedFor("asha", T0)).isEmpty();
        assertThat(policy.trackedKeys()).isZero();
    }

    @Test
    void rejectsNonsenseSettings() {
        assertThatThrownBy(() -> new SlidingWindowAttemptPolicy(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlidingWindowAttemptPolicy(3, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theMessageSaysHowLongToWait() {
        assertThat(new TooManyAttemptsException(Duration.ofSeconds(61)).getMessage()).contains("2 minutes");
        assertThat(new TooManyAttemptsException(Duration.ofSeconds(5)).getMessage()).contains("1 minute,");
    }
}
