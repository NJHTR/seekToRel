package com.reportai.auth.ratelimit;

/**
 * Guard for public credential endpoints (register, login, refresh).
 *
 * <p>Implementations must bound the number of attempts per identity within a
 * fixed window. Implementations are allowed to fail open when the backing
 * store is unavailable so a rate-limiter outage never locks every user out;
 * the guard is defense in depth, not the only credential control.
 */
public interface AuthAttemptGuard {

    /**
     * Records one attempt and throws {@link com.reportai.common.exception.BusinessException}
     * with {@code RATE_LIMITED} when the window budget is exhausted.
     */
    void checkAndRecord(String operation, String identity, String clientAddress);
}
