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
    private int maxTotalSubscribers = 4_096;
    private int maxReplayEventsPerTask = 128;
    private int maxReplayTasks = 1_000;

    public Duration getEmitterTimeout() {
        return emitterTimeout;
    }

    public void setEmitterTimeout(Duration emitterTimeout) {
        this.emitterTimeout = emitterTimeout;
    }

    public int getMaxSubscribersPerTask() {
        return maxSubscribersPerTask;
    }

    public int getMaxTotalSubscribers() {
        return maxTotalSubscribers;
    }

    public void setMaxTotalSubscribers(int maxTotalSubscribers) {
        this.maxTotalSubscribers = maxTotalSubscribers;
    }

    public void setMaxSubscribersPerTask(int maxSubscribersPerTask) {
        this.maxSubscribersPerTask = maxSubscribersPerTask;
    }

    public int getMaxReplayEventsPerTask() {
        return maxReplayEventsPerTask;
    }

    public void setMaxReplayEventsPerTask(int maxReplayEventsPerTask) {
        this.maxReplayEventsPerTask = maxReplayEventsPerTask;
    }

    public int getMaxReplayTasks() {
        return maxReplayTasks;
    }

    public void setMaxReplayTasks(int maxReplayTasks) {
        this.maxReplayTasks = maxReplayTasks;
    }
}
