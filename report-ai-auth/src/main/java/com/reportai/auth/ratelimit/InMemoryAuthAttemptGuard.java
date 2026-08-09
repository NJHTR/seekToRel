package com.reportai.auth.ratelimit;

import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded in-memory fixed-window guard for public credential endpoints.
 *
 * <p>Default backend for local development and single-instance deployments.
 * Per-JVM state only; multi-instance deployments must use the Redis backend.
 */
public final class InMemoryAuthAttemptGuard implements AuthAttemptGuard {
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Clock clock;
    private final int maxAttemptsPerWindow;
    private final Duration window;
    private final Map<String, AttemptWindow> windows = new ConcurrentHashMap<>();

    public InMemoryAuthAttemptGuard(int maxAttemptsPerWindow, Duration window) {
        this(Clock.systemUTC(), maxAttemptsPerWindow, window);
    }

    InMemoryAuthAttemptGuard(Clock clock, int maxAttemptsPerWindow, Duration window) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxAttemptsPerWindow = requirePositive(maxAttemptsPerWindow, "maxAttemptsPerWindow");
        this.window = Objects.requireNonNull(window, "window");
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
    }

    @Override
    public void checkAndRecord(String operation, String identity, String clientAddress) {
        String key = AuthRateLimitKey.of(operation, identity, clientAddress);
        Instant now = Instant.now(clock);
        if (windows.size() >= MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
            if (windows.size() >= MAX_TRACKED_KEYS && !windows.containsKey(key)) {
                throw limited();
            }
        }
        AttemptWindow current = windows.compute(key, (ignored, existing) -> {
            if (existing == null || !existing.expiresAt().isAfter(now)) {
                return new AttemptWindow(1, now.plus(window));
            }
            return new AttemptWindow(existing.count() + 1, existing.expiresAt());
        });
        if (current.count() > maxAttemptsPerWindow) {
            throw limited();
        }
    }

    private static BusinessException limited() {
        return new BusinessException(ErrorCode.RATE_LIMITED, "too many authentication attempts");
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private record AttemptWindow(int count, Instant expiresAt) {
    }
}