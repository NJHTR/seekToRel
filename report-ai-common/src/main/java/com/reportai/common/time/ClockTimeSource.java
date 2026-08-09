package com.reportai.common.time;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** TimeSource adapter for the JDK Clock already used by auth and application services. */
public final class ClockTimeSource implements TimeSource {
    private final Clock clock;

    public ClockTimeSource(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Instant now() {
        return Instant.now(clock);
    }
}
