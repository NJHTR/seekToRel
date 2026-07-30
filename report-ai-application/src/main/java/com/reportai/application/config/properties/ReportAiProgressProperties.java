package com.reportai.application.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Limits for live task progress streams. */
@Validated
@ConfigurationProperties(prefix = "report-ai.progress")
public class ReportAiProgressProperties {
    private Duration emitterTimeout = Duration.ofMinutes(15);
    private int maxSubscribersPerTask = 64;

    public Duration getEmitterTimeout() {
        return emitterTimeout;
    }

    public void setEmitterTimeout(Duration emitterTimeout) {
        this.emitterTimeout = emitterTimeout;
    }

    public int getMaxSubscribersPerTask() {
        return maxSubscribersPerTask;
    }

    public void setMaxSubscribersPerTask(int maxSubscribersPerTask) {
        this.maxSubscribersPerTask = maxSubscribersPerTask;
    }
}
