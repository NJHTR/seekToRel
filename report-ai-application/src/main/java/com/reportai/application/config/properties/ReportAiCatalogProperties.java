package com.reportai.application.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Detached-signature settings for the administrator-owned offline catalog. */
@ConfigurationProperties(prefix = "report-ai.runtime.catalog")
public class ReportAiCatalogProperties {
    private boolean enabled;
    private String path;
    private String signaturePath;
    private String publicKey;
    private String publicKeyPath;
    private long maxBytes = 1024L * 1024;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getSignaturePath() { return signaturePath; }
    public void setSignaturePath(String signaturePath) { this.signaturePath = signaturePath; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    public String getPublicKeyPath() { return publicKeyPath; }
    public void setPublicKeyPath(String publicKeyPath) { this.publicKeyPath = publicKeyPath; }
    public long getMaxBytes() { return maxBytes; }
    public void setMaxBytes(long maxBytes) { this.maxBytes = maxBytes; }
}
