package com.reportai.runtime;

/** Result of the installer container and its task-scoped dependency volume. */
public record RuntimeInstallResult(ExecutionResult result, String dependencyVolume) {
    public RuntimeInstallResult {
        if (result == null) {
            throw new IllegalArgumentException("installer result is required");
        }
        if (dependencyVolume != null && (dependencyVolume.isBlank() || dependencyVolume.length() > 256
                || !dependencyVolume.matches("[A-Za-z0-9_.-]+"))) {
            throw new IllegalArgumentException("dependency volume is invalid");
        }
    }

    public boolean succeeded() {
        return result.status() == ExecutionResult.Status.SUCCEEDED;
    }
}
