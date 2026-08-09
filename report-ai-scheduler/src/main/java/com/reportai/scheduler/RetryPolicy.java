package com.reportai.scheduler;

import java.time.Duration;

public record RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {
    public RetryPolicy {
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 100");
        }
        if (initialBackoff == null || maxBackoff == null || initialBackoff.isNegative()
                || maxBackoff.isNegative() || maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("invalid retry backoff");
        }
    }

    public Duration backoffForAttempt(int attempt) {
        if (attempt <= 1 || initialBackoff.isZero()) {
            return Duration.ZERO;
        }
        long multiplier = 1L << Math.min(attempt - 2, 30);
        try {
            return initialBackoff.multipliedBy(multiplier).compareTo(maxBackoff) > 0
                    ? maxBackoff : initialBackoff.multipliedBy(multiplier);
        } catch (ArithmeticException ignored) {
            return maxBackoff;
        }
    }
}
