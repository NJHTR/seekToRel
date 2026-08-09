package com.reportai.auth.jwt;

import com.reportai.auth.port.AuthenticationResolver;
import com.reportai.auth.port.JwtAuthenticationResolver;
import com.reportai.auth.web.TenantContextFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Spring Boot wiring for first-party local login and JWT verification. */
@AutoConfiguration
@EnableConfigurationProperties(LocalAuthProperties.class)
@ConditionalOnProperty(prefix = "report-ai.auth.local", name = "enabled", havingValue = "true")
public class LocalAuthAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public LocalJwtTokenService localJwtTokenService(LocalAuthProperties properties) {
        return new LocalJwtTokenService(properties);
    }

    @Bean
    @ConditionalOnMissingBean(AuthenticationResolver.class)
    public AuthenticationResolver localAuthenticationResolver(LocalJwtTokenService tokenService) {
        return new JwtAuthenticationResolver(tokenService);
    }

    @Bean
    @ConditionalOnMissingBean(TenantContextFilter.class)
    public TenantContextFilter localTenantContextFilter(AuthenticationResolver resolver) {
        return new TenantContextFilter(resolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public org.springframework.security.crypto.password.PasswordEncoder localPasswordEncoder(
            LocalAuthProperties properties) {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(properties.getBcryptStrength());
    }
}
