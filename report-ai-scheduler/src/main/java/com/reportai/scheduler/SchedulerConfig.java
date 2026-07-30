package com.reportai.scheduler;

import java.time.Duration;

public record SchedulerConfig(int workerCount, int queueCapacity, Duration leaseDuration,
                              Duration executionTimeout, int maxProgressMessageLength) {
    public SchedulerConfig {
        if (workerCount < 1 || workerCount > 256 || queueCapacity < 1 || queueCapacity > 100_000) {
            throw new IllegalArgumentException("workerCount/queueCapacity out of bounds");
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()
                || executionTimeout == null || executionTimeout.isZero() || executionTimeout.isNegative()) {
            throw new IllegalArgumentException("leaseDuration and executionTimeout must be positive");
        }
        if (maxProgressMessageLength < 1 || maxProgressMessageLength > 16_384) {
            throw new IllegalArgumentException("invalid progress message limit");
        }
    }

    public static SchedulerConfig defaults() {
        return new SchedulerConfig(4, 256, Duration.ofMinutes(2), Duration.ofMinutes(10), 2_000);
    }
}
