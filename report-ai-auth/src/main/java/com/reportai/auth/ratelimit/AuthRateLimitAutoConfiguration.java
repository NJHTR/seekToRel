package com.reportai.auth.ratelimit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Selects the {@link AuthAttemptGuard} implementation for public credential
 * endpoints. Defaults to the in-memory backend so a fresh deployment has no
 * Redis prerequisite. When {@code backend=redis} the Redis guard is active
 * only if a {@link StringRedisTemplate} bean is available.
 */
@AutoConfiguration
@EnableConfigurationProperties(AuthRateLimitProperties.class)
@ConditionalOnMissingBean(AuthAttemptGuard.class)
public class AuthRateLimitAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "report-ai.auth.rate-limit", name = "backend",
            havingValue = "memory", matchIfMissing = true)
    public AuthAttemptGuard inMemoryAuthAttemptGuard(AuthRateLimitProperties properties) {
        properties.validate();
        return new InMemoryAuthAttemptGuard(properties.getMaxAttemptsPerWindow(),
                Duration.ofSeconds(properties.getWindowSeconds()));
    }

    @Bean
    @ConditionalOnProperty(prefix = "report-ai.auth.rate-limit", name = "backend", havingValue = "redis")
    @ConditionalOnBean(StringRedisTemplate.class)
    public AuthAttemptGuard redisAuthAttemptGuard(AuthRateLimitProperties properties, StringRedisTemplate redis) {
        properties.validate();
        return new RedisAuthAttemptGuard(redis, properties.getRedisKeyPrefix(),
                properties.getMaxAttemptsPerWindow(), Duration.ofSeconds(properties.getWindowSeconds()));
    }
}