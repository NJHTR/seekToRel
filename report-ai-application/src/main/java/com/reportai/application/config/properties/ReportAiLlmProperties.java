package com.reportai.application.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Provider-neutral LLM connection settings. API keys are never given defaults or logged. */
@Validated
@ConfigurationProperties(prefix = "report-ai.llm")
public class ReportAiLlmProperties {
    private boolean enabled;
    private String provider;
    private String endpoint;
    private String model;
    private String apiKey;
    private String reasoningEffort;
    private String coderReasoningEffort = "medium";
    private String coderFallbackReasoningEffort = "low";
    private boolean coderFallbackEnabled = true;
    /** Code responses often contain complete DOCX/XML transformation programs. */
    private int coderMaxOutputTokens = 8_192;
    private Duration coderTimeout = Duration.ofSeconds(105);
    /** Maximum image attachments sent to one planner/coder request. */
    private int visionMaxAttachments = 16;
    /** Maximum bytes for one image included in a multimodal request. */
    private long visionMaxAttachmentBytes = 20L * 1024 * 1024;
    /** Aggregate image bytes included in a multimodal request. */
    private long visionMaxAttachmentTotalBytes = 40L * 1024 * 1024;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(180);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public String getCoderReasoningEffort() {
        return coderReasoningEffort;
    }

    public void setCoderReasoningEffort(String coderReasoningEffort) {
        this.coderReasoningEffort = coderReasoningEffort;
    }

    public String getCoderFallbackReasoningEffort() {
        return coderFallbackReasoningEffort;
    }

    public void setCoderFallbackReasoningEffort(String coderFallbackReasoningEffort) {
        this.coderFallbackReasoningEffort = coderFallbackReasoningEffort;
    }

    public boolean isCoderFallbackEnabled() {
        return coderFallbackEnabled;
    }

    public void setCoderFallbackEnabled(boolean coderFallbackEnabled) {
        this.coderFallbackEnabled = coderFallbackEnabled;
    }

    public int getCoderMaxOutputTokens() {
        return coderMaxOutputTokens;
    }

    public void setCoderMaxOutputTokens(int coderMaxOutputTokens) {
        this.coderMaxOutputTokens = coderMaxOutputTokens;
    }

    public Duration getCoderTimeout() {
        return coderTimeout;
    }

    public void setCoderTimeout(Duration coderTimeout) {
        this.coderTimeout = coderTimeout;
    }

    public int getVisionMaxAttachments() {
        return visionMaxAttachments;
    }

    public void setVisionMaxAttachments(int visionMaxAttachments) {
        this.visionMaxAttachments = visionMaxAttachments;
    }

    public long getVisionMaxAttachmentBytes() {
        return visionMaxAttachmentBytes;
    }

    public void setVisionMaxAttachmentBytes(long visionMaxAttachmentBytes) {
        this.visionMaxAttachmentBytes = visionMaxAttachmentBytes;
    }

    public long getVisionMaxAttachmentTotalBytes() {
        return visionMaxAttachmentTotalBytes;
    }

    public void setVisionMaxAttachmentTotalBytes(long visionMaxAttachmentTotalBytes) {
        this.visionMaxAttachmentTotalBytes = visionMaxAttachmentTotalBytes;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
