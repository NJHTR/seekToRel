package com.reportai.application.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Request metrics and slow-request logging settings. */
@Validated
@ConfigurationProperties(prefix = "report-ai.observability")
public class ReportAiObservabilityProperties {
    private Duration slowRequestThreshold = Duration.ofSeconds(2);
    private boolean logAllRequests;
    private boolean metricsEnabled = true;
    private int maxPathLength = 256;

    public Duration getSlowRequestThreshold() {
        return slowRequestThreshold;
    }

    public void setSlowRequestThreshold(Duration slowRequestThreshold) {
        this.slowRequestThreshold = slowRequestThreshold;
    }

    public boolean isLogAllRequests() {
        return logAllRequests;
    }

    public void setLogAllRequests(boolean logAllRequests) {
        this.logAllRequests = logAllRequests;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    public int getMaxPathLength() {
        return maxPathLength;
    }

    public void setMaxPathLength(int maxPathLength) {
        this.maxPathLength = maxPathLength;
    }
}
