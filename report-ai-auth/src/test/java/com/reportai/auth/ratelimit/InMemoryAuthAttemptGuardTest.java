package com.reportai.auth.ratelimit;

import com.reportai.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryAuthAttemptGuardTest {

    private final MutableClock clock = new MutableClock("2026-08-03T00:00:00Z");

    @Test
    void enforcesBudgetWithinWindowAndResetsAfterExpiry() {
        InMemoryAuthAttemptGuard guard = new InMemoryAuthAttemptGuard(clock, 2, Duration.ofSeconds(60));

        assertDoesNotThrow(() -> guard.checkAndRecord("login", "alice", "10.0.0.1"));
        assertDoesNotThrow(() -> guard.checkAndRecord("login", "alice", "10.0.0.1"));
        assertThrows(BusinessException.class, () -> guard.checkAndRecord("login", "alice", "10.0.0.1"));

        assertDoesNotThrow(() -> guard.checkAndRecord("login", "bob", "10.0.0.1"));

        clock.advance(Duration.ofSeconds(61));
        assertDoesNotThrow(() -> guard.checkAndRecord("login", "alice", "10.0.0.1"));
    }

    @Test
    void treatsIdentitiesAndAddressesAsSeparateBuckets() {
        InMemoryAuthAttemptGuard guard = new InMemoryAuthAttemptGuard(clock, 1, Duration.ofSeconds(60));

        assertDoesNotThrow(() -> guard.checkAndRecord("login", "alice", "10.0.0.1"));
        assertDoesNotThrow(() -> guard.checkAndRecord("login", "alice", "10.0.0.2"));
        assertDoesNotThrow(() -> guard.checkAndRecord("login", "bob", "10.0.0.1"));
        assertThrows(BusinessException.class, () -> guard.checkAndRecord("login", "alice", "10.0.0.1"));
    }

    @Test
    void refusesInvalidConstructorArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new InMemoryAuthAttemptGuard(clock, 0, Duration.ofSeconds(60)));
        assertThrows(IllegalArgumentException.class,
                () -> new InMemoryAuthAttemptGuard(clock, 2, Duration.ofSeconds(0)));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(String iso) {
            now = Instant.parse(iso);
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}