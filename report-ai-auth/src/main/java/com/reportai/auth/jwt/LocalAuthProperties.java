package com.reportai.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Configuration for the first-party local JWT issuer. */
@ConfigurationProperties(prefix = "report-ai.auth.local")
public class LocalAuthProperties {
    private boolean enabled;
    private String signingSecret;
    private String issuer = "report-ai";
    private String audience = "report-ai-web";
    private String keyId = "local-v1";
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(30);
    private int bcryptStrength = 12;

    public void validate() {
        if (!enabled) {
            return;
        }
        if (signingSecret == null || signingSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("report-ai.auth.local.signing-secret must contain at least 32 bytes");
        }
        requireText(issuer, "issuer", 512);
        requireText(audience, "audience", 256);
        requireText(keyId, "keyId", 128);
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()
                || accessTokenTtl.compareTo(Duration.ofHours(2)) > 0) {
            throw new IllegalArgumentException("access-token-ttl must be between 1 second and 2 hours");
        }
        if (refreshTokenTtl == null || refreshTokenTtl.compareTo(Duration.ofHours(1)) < 0
                || refreshTokenTtl.compareTo(Duration.ofDays(90)) > 0) {
            throw new IllegalArgumentException("refresh-token-ttl must be between 1 hour and 90 days");
        }
        if (bcryptStrength < 10 || bcryptStrength > 15) {
            throw new IllegalArgumentException("bcrypt-strength must be between 10 and 15");
        }
    }

    private static void requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getSigningSecret() { return signingSecret; }
    public void setSigningSecret(String signingSecret) { this.signingSecret = signingSecret; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
    public Duration getAccessTokenTtl() { return accessTokenTtl; }
    public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }
    public Duration getRefreshTokenTtl() { return refreshTokenTtl; }
    public void setRefreshTokenTtl(Duration refreshTokenTtl) { this.refreshTokenTtl = refreshTokenTtl; }
    public int getBcryptStrength() { return bcryptStrength; }
    public void setBcryptStrength(int bcryptStrength) { this.bcryptStrength = bcryptStrength; }
}
