package com.reportai.auth.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate limiting for public credential endpoints.
 *
 * <p>{@code backend} selects the guard implementation:
 * <ul>
 *   <li>{@code memory} (default) - per-JVM fixed window, no infrastructure.</li>
 *   <li>{@code redis} - distributed fixed window, requires Redis.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "report-ai.auth.rate-limit")
public class AuthRateLimitProperties {
    private String backend = "memory";
    private int maxAttemptsPerWindow = 10;
    private long windowSeconds = 60;
    private String redisKeyPrefix = "reportai:auth-rate:";

    public void validate() {
        if (!"memory".equals(backend) && !"redis".equals(backend)) {
            throw new IllegalArgumentException("report-ai.auth.rate-limit.backend must be 'memory' or 'redis'");
        }
        if (maxAttemptsPerWindow < 1 || maxAttemptsPerWindow > 10_000) {
            throw new IllegalArgumentException("report-ai.auth.rate-limit.max-attempts-per-window must be between 1 and 10000");
        }
        if (windowSeconds < 1 || windowSeconds > 86_400) {
            throw new IllegalArgumentException("report-ai.auth.rate-limit.window-seconds must be between 1 and 86400");
        }
        if (redisKeyPrefix == null || redisKeyPrefix.isBlank() || redisKeyPrefix.length() > 128) {
            throw new IllegalArgumentException("report-ai.auth.rate-limit.redis-key-prefix is invalid");
        }
    }

    public String getBackend() { return backend; }
    public void setBackend(String backend) { this.backend = backend; }
    public int getMaxAttemptsPerWindow() { return maxAttemptsPerWindow; }
    public void setMaxAttemptsPerWindow(int maxAttemptsPerWindow) { this.maxAttemptsPerWindow = maxAttemptsPerWindow; }
    public long getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(long windowSeconds) { this.windowSeconds = windowSeconds; }
    public String getRedisKeyPrefix() { return redisKeyPrefix; }
    public void setRedisKeyPrefix(String redisKeyPrefix) { this.redisKeyPrefix = redisKeyPrefix; }
}