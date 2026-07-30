package com.reportai.runtime;

import java.time.Duration;

public record ExecutionPolicy(Duration timeout, long maxOutputBytes, int cpuLimit, long memoryBytes,
                              boolean networkEnabled) {
    public ExecutionPolicy {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (maxOutputBytes <= 0 || cpuLimit <= 0 || memoryBytes <= 0) {
            throw new IllegalArgumentException("runtime limits must be positive");
        }
    }

    public static ExecutionPolicy safeDefaults() {
        return new ExecutionPolicy(Duration.ofMinutes(2), 2 * 1024 * 1024L, 1, 512 * 1024 * 1024L, false);
    }
}
