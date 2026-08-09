package com.reportai.application.config.properties;

import com.reportai.scheduler.SchedulerConfig;
import com.reportai.scheduler.RetryPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Externalized limits for the durable task dispatcher.
 *
 * <p>The worker pool and queue are deliberately bounded. Recovery runs in a separate,
 * single-threaded poller so a process restart or an expired lease cannot leave tasks stuck
 * in the database indefinitely.</p>
 */
@Validated
@ConfigurationProperties(prefix = "report-ai.scheduler")
public class ReportAiSchedulerProperties {
    private boolean enabled = true;
    private int workerCount = 4;
    private int queueCapacity = 256;
    private Duration leaseDuration = Duration.ofMinutes(2);
    private Duration executionTimeout = Duration.ofMinutes(10);
    private int maxProgressMessageLength = 2_000;
    private Duration recoveryInterval = Duration.ofSeconds(5);
    private Duration recoveryInitialDelay = Duration.ZERO;
    private int dispatchBatchSize = 16;
    private int reportTaskMaxAttempts = 3;
    private Duration reportTaskInitialBackoff = Duration.ofSeconds(5);
    private Duration reportTaskMaxBackoff = Duration.ofMinutes(5);
    private Duration outboxPublishedRetention = Duration.ofDays(7);
    private int outboxCleanupBatchSize = 500;

    public SchedulerConfig toSchedulerConfig() {
        return new SchedulerConfig(workerCount, queueCapacity, leaseDuration,
                executionTimeout, maxProgressMessageLength);
    }

    public RetryPolicy reportTaskRetryPolicy() {
        return new RetryPolicy(reportTaskMaxAttempts, reportTaskInitialBackoff, reportTaskMaxBackoff);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getWorkerCount() {
        return workerCount;
    }

    public void setWorkerCount(int workerCount) {
        this.workerCount = workerCount;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public Duration getExecutionTimeout() {
        return executionTimeout;
    }

    public void setExecutionTimeout(Duration executionTimeout) {
        this.executionTimeout = executionTimeout;
    }

    public int getMaxProgressMessageLength() {
        return maxProgressMessageLength;
    }

    public void setMaxProgressMessageLength(int maxProgressMessageLength) {
        this.maxProgressMessageLength = maxProgressMessageLength;
    }

    public Duration getRecoveryInterval() {
        return recoveryInterval;
    }

    public void setRecoveryInterval(Duration recoveryInterval) {
        this.recoveryInterval = recoveryInterval;
    }

    public Duration getRecoveryInitialDelay() {
        return recoveryInitialDelay;
    }

    public void setRecoveryInitialDelay(Duration recoveryInitialDelay) {
        this.recoveryInitialDelay = recoveryInitialDelay;
    }

    public int getDispatchBatchSize() {
        return dispatchBatchSize;
    }

    public void setDispatchBatchSize(int dispatchBatchSize) {
        this.dispatchBatchSize = dispatchBatchSize;
    }

    public int getReportTaskMaxAttempts() {
        return reportTaskMaxAttempts;
    }

    public void setReportTaskMaxAttempts(int reportTaskMaxAttempts) {
        this.reportTaskMaxAttempts = reportTaskMaxAttempts;
    }

    public Duration getReportTaskInitialBackoff() {
        return reportTaskInitialBackoff;
    }

    public void setReportTaskInitialBackoff(Duration reportTaskInitialBackoff) {
        this.reportTaskInitialBackoff = reportTaskInitialBackoff;
    }

    public Duration getReportTaskMaxBackoff() {
        return reportTaskMaxBackoff;
    }

    public void setReportTaskMaxBackoff(Duration reportTaskMaxBackoff) {
        this.reportTaskMaxBackoff = reportTaskMaxBackoff;
    }

    public Duration getOutboxPublishedRetention() {
        return outboxPublishedRetention;
    }

    public void setOutboxPublishedRetention(Duration outboxPublishedRetention) {
        this.outboxPublishedRetention = outboxPublishedRetention;
    }

    public int getOutboxCleanupBatchSize() {
        return outboxCleanupBatchSize;
    }

    public void setOutboxCleanupBatchSize(int outboxCleanupBatchSize) {
        this.outboxCleanupBatchSize = outboxCleanupBatchSize;
    }
}
