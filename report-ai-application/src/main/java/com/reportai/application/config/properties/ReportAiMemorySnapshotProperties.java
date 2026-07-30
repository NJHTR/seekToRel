package com.reportai.application.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Operational bounds for durable conversation-memory snapshot maintenance. */
@Validated
@ConfigurationProperties(prefix = "report-ai.memory-snapshot")
public class ReportAiMemorySnapshotProperties {
    private boolean enabled = true;
    private Duration interval = Duration.ofMinutes(10);
    private Duration initialDelay = Duration.ofMinutes(1);
    private int batchSize = 50;
    private int maxSummaryChars = 16_384;
    private int maxAttempts = 3;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(Duration initialDelay) {
        this.initialDelay = initialDelay;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxSummaryChars() {
        return maxSummaryChars;
    }

    public void setMaxSummaryChars(int maxSummaryChars) {
        this.maxSummaryChars = maxSummaryChars;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }
}
