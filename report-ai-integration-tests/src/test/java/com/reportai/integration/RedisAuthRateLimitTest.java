package com.reportai.integration;

import com.reportai.auth.ratelimit.RedisAuthAttemptGuard;
import com.reportai.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Real Redis evidence for the distributed credential guard. Built only by the
 * explicit {@code -Pintegration} Maven profile.
 */
@Testcontainers
class RedisAuthRateLimitTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            "redis@sha256:e7723ff73d963f5cc6d9c4643ea3d989527a402a319239054e9472a7fb9219a2")
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));

    @Test
    void enforcesFixedWindowAndResetsAfterExpiry() throws Exception {
        String host = REDIS.getHost();
        int port = REDIS.getMappedPort(6379);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(host, port);
        factory.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            RedisAuthAttemptGuard guard = new RedisAuthAttemptGuard(redis, "it:auth-rate:", 3, Duration.ofSeconds(2));

            assertDoesNotThrow(() -> guard.checkAndRecord("login", "alice@example.com", "10.0.0.1"));
            assertDoesNotThrow(() -> guard.checkAndRecord("login", "alice@example.com", "10.0.0.1"));
            assertDoesNotThrow(() -> guard.checkAndRecord("login", "alice@example.com", "10.0.0.1"));
            assertThrows(BusinessException.class,
                    () -> guard.checkAndRecord("login", "alice@example.com", "10.0.0.1"));

            assertDoesNotThrow(() -> guard.checkAndRecord("login", "bob@example.com", "10.0.0.1"));
            assertDoesNotThrow(() -> guard.checkAndRecord("login", "alice@example.com", "10.0.0.2"));

            Thread.sleep(2_500);
            assertDoesNotThrow(() -> guard.checkAndRecord("login", "alice@example.com", "10.0.0.1"));
        } finally {
            factory.destroy();
        }
    }
}
