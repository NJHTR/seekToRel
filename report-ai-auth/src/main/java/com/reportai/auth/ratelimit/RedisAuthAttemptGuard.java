package com.reportai.auth.ratelimit;

import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Distributed fixed-window guard backed by Redis.
 *
 * <p>Uses a single atomic Lua {@code INCR}/{@code EXPIRE} round trip so a
 * concurrent burst cannot exceed the budget through check-then-act races.
 * Buckets are tenant/instance neutral and expire after the window, so the key
 * space is self-cleaning.
 *
 * <p>Fail-open policy: when the backing store is unavailable the attempt is
 * allowed and a warning is logged. Credential endpoints remain protected by
 * server-side password verification; this guard is defense in depth.
 */
public final class RedisAuthAttemptGuard implements AuthAttemptGuard {
    private static final Logger LOG = LoggerFactory.getLogger(RedisAuthAttemptGuard.class);

    private static final RedisScript<Long> FIXED_WINDOW = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]); "
                    + "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; "
                    + "if current > tonumber(ARGV[2]) then return 0; end; "
                    + "return 1;",
            Long.class);

    private final StringRedisTemplate redis;
    private final String keyPrefix;
    private final int maxAttemptsPerWindow;
    private final Duration window;

    public RedisAuthAttemptGuard(StringRedisTemplate redis, String keyPrefix,
                                 int maxAttemptsPerWindow, Duration window) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
        if (keyPrefix.length() > 128) {
            throw new IllegalArgumentException("keyPrefix is too long");
        }
        this.maxAttemptsPerWindow = requirePositive(maxAttemptsPerWindow, "maxAttemptsPerWindow");
        this.window = Objects.requireNonNull(window, "window");
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
    }

    @Override
    public void checkAndRecord(String operation, String identity, String clientAddress) {
        String bucket = AuthRateLimitKey.of(operation, identity, clientAddress);
        String key = keyPrefix + bucket;
        try {
            Long admitted = redis.execute(FIXED_WINDOW, List.of(key),
                    String.valueOf(window.toSeconds()), String.valueOf(maxAttemptsPerWindow));
            if (admitted == null || admitted == 0L) {
                throw new BusinessException(ErrorCode.RATE_LIMITED, "too many authentication attempts");
            }
        } catch (BusinessException failure) {
            throw failure;
        } catch (RuntimeException unavailable) {
            LOG.warn("Redis auth rate limiter unavailable; failing open for key {}", key, unavailable);
        }
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}