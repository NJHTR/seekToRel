package com.reportai.auth.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.reportai.auth.port.AuthenticationResolver;
import com.reportai.auth.port.JwtAuthenticationResolver;
import com.reportai.auth.port.JwtCredentialVerifier;
import com.reportai.auth.port.JwtKeyResolver;
import com.reportai.auth.web.TenantContextFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Locale;
import java.util.Set;

/** Spring Boot wiring for the production JWT/JWKS authentication adapter. */
@AutoConfiguration
@EnableConfigurationProperties(ReportAiJwtProperties.class)
@ConditionalOnProperty(prefix = "report-ai.auth.jwt", name = "enabled", havingValue = "true")
public class JwtAuthenticationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public JwtKeyResolver jwtKeyResolver(ReportAiJwtProperties properties) {
        properties.validate();
        return new RemoteJwksJwtKeyResolver(properties.getJwksUri(), properties.getConnectTimeout(),
                properties.getReadTimeout(), properties.getResponseSizeLimit(), properties.getCacheLifespan(),
                properties.getCacheRefresh(), properties.isAllowInsecureHttp());
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtCredentialVerifier jwtCredentialVerifier(JwtKeyResolver keyResolver,
                                                       ReportAiJwtProperties properties) {
        Set<JWSAlgorithm> algorithms = properties.getAllowedAlgorithms().stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> JWSAlgorithm.parse(value.toUpperCase(Locale.ROOT)))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        JwtVerificationPolicy policy = new JwtVerificationPolicy(properties.getIssuer(), properties.getAudience(),
                algorithms, properties.getClockSkew(), JwtClaimNames.defaults());
        return new NimbusJwtCredentialVerifier(keyResolver, policy);
    }

    @Bean
    @ConditionalOnMissingBean(AuthenticationResolver.class)
    public AuthenticationResolver authenticationResolver(JwtCredentialVerifier verifier) {
        return new JwtAuthenticationResolver(verifier);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantContextFilter tenantContextFilter(AuthenticationResolver resolver) {
        return new TenantContextFilter(resolver);
    }
}
