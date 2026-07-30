package com.reportai.application.config.properties;

import com.reportai.runtime.ExecutionPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.nio.file.Path;

/** Limits for untrusted code execution. */
@Validated
@ConfigurationProperties(prefix = "report-ai.runtime")
public class ReportAiRuntimeProperties {
    private boolean enabled;
    private String workspaceRoot = "./workspaces";
    private Duration timeout = Duration.ofMinutes(2);
    private long maxOutputBytes = 2L * 1024 * 1024;
    private int cpuLimit = 1;
    private long memoryBytes = 512L * 1024 * 1024;
    private boolean networkEnabled;
    private int maxArtifacts = 100;
    private long maxArtifactBytes = 50L * 1024 * 1024;
    private int maxCpuLimit = 4;
    private long maxMemoryBytes = 4L * 1024 * 1024 * 1024;
    private Duration maxTimeout = Duration.ofMinutes(10);

    public ExecutionPolicy toExecutionPolicy() {
        return new ExecutionPolicy(timeout, maxOutputBytes, cpuLimit, memoryBytes, networkEnabled);
    }

    public com.reportai.runtime.RuntimeConfiguration toConfiguration() {
        return new com.reportai.runtime.RuntimeConfiguration(Path.of(workspaceRoot),
                com.reportai.runtime.RuntimeConfiguration.productionDefaults(Path.of(workspaceRoot)).languages(),
                maxArtifacts, maxArtifactBytes, maxCpuLimit, maxMemoryBytes, maxTimeout, maxOutputBytes);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getWorkspaceRoot() { return workspaceRoot; }
    public void setWorkspaceRoot(String workspaceRoot) { this.workspaceRoot = workspaceRoot; }
    public int getMaxArtifacts() { return maxArtifacts; }
    public void setMaxArtifacts(int maxArtifacts) { this.maxArtifacts = maxArtifacts; }
    public long getMaxArtifactBytes() { return maxArtifactBytes; }
    public void setMaxArtifactBytes(long maxArtifactBytes) { this.maxArtifactBytes = maxArtifactBytes; }
    public int getMaxCpuLimit() { return maxCpuLimit; }
    public void setMaxCpuLimit(int maxCpuLimit) { this.maxCpuLimit = maxCpuLimit; }
    public long getMaxMemoryBytes() { return maxMemoryBytes; }
    public void setMaxMemoryBytes(long maxMemoryBytes) { this.maxMemoryBytes = maxMemoryBytes; }
    public Duration getMaxTimeout() { return maxTimeout; }
    public void setMaxTimeout(Duration maxTimeout) { this.maxTimeout = maxTimeout; }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public long getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(long maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    public int getCpuLimit() {
        return cpuLimit;
    }

    public void setCpuLimit(int cpuLimit) {
        this.cpuLimit = cpuLimit;
    }

    public long getMemoryBytes() {
        return memoryBytes;
    }

    public void setMemoryBytes(long memoryBytes) {
        this.memoryBytes = memoryBytes;
    }

    public boolean isNetworkEnabled() {
        return networkEnabled;
    }

    public void setNetworkEnabled(boolean networkEnabled) {
        this.networkEnabled = networkEnabled;
    }
}
