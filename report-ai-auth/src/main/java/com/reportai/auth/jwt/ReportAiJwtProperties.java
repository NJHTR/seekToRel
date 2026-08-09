package com.reportai.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/** Externalized settings for the JWT/JWKS authentication adapter. */
@ConfigurationProperties(prefix = "report-ai.auth.jwt")
public class ReportAiJwtProperties {
    private boolean enabled;
    private URI jwksUri;
    private String issuer;
    private String audience;
    private Set<String> allowedAlgorithms = new LinkedHashSet<>(Set.of("RS256"));
    private Duration clockSkew = Duration.ofSeconds(30);
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);
    private int responseSizeLimit = 1_048_576;
    private Duration cacheLifespan = Duration.ofMinutes(15);
    private Duration cacheRefresh = Duration.ofMinutes(5);
    private boolean allowInsecureHttp;

    public void validate() {
        if (!enabled) {
            return;
        }
        if (jwksUri == null || issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("report-ai.auth.jwt requires jwks-uri, issuer and audience when enabled");
        }
        if (allowedAlgorithms == null || allowedAlgorithms.isEmpty()) {
            throw new IllegalArgumentException("report-ai.auth.jwt.allowed-algorithms must not be empty");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public URI getJwksUri() { return jwksUri; }
    public void setJwksUri(URI jwksUri) { this.jwksUri = jwksUri; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public Set<String> getAllowedAlgorithms() { return allowedAlgorithms; }
    public void setAllowedAlgorithms(Set<String> allowedAlgorithms) { this.allowedAlgorithms = allowedAlgorithms; }
    public Duration getClockSkew() { return clockSkew; }
    public void setClockSkew(Duration clockSkew) { this.clockSkew = clockSkew; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getResponseSizeLimit() { return responseSizeLimit; }
    public void setResponseSizeLimit(int responseSizeLimit) { this.responseSizeLimit = responseSizeLimit; }
    public Duration getCacheLifespan() { return cacheLifespan; }
    public void setCacheLifespan(Duration cacheLifespan) { this.cacheLifespan = cacheLifespan; }
    public Duration getCacheRefresh() { return cacheRefresh; }
    public void setCacheRefresh(Duration cacheRefresh) { this.cacheRefresh = cacheRefresh; }
    public boolean isAllowInsecureHttp() { return allowInsecureHttp; }
    public void setAllowInsecureHttp(boolean allowInsecureHttp) { this.allowInsecureHttp = allowInsecureHttp; }
}
