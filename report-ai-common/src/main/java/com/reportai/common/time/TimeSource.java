package com.reportai.common.time;

import java.time.Instant;

/** Injectable wall-clock port for deterministic domain tests and replay. */
@FunctionalInterface
public interface TimeSource {
    Instant now();

    static TimeSource systemUtc() {
        return () -> Instant.now(java.time.Clock.systemUTC());
    }
}
