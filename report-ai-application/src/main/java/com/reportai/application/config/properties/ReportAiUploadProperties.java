package com.reportai.application.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

/** Upload limits and allow-list configuration. */
@Validated
@ConfigurationProperties(prefix = "report-ai.upload")
public class ReportAiUploadProperties {
    private long maxBytes = 20L * 1024 * 1024;
    private int maxFileNameLength = 255;
    private Set<String> allowedMediaTypes = Set.of(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private Set<String> allowedExtensions = Set.of(".docx");

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public int getMaxFileNameLength() {
        return maxFileNameLength;
    }

    public void setMaxFileNameLength(int maxFileNameLength) {
        this.maxFileNameLength = maxFileNameLength;
    }

    public Set<String> getAllowedMediaTypes() {
        return allowedMediaTypes;
    }

    public void setAllowedMediaTypes(Set<String> allowedMediaTypes) {
        this.allowedMediaTypes = allowedMediaTypes;
    }

    public Set<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(Set<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }
}
