package com.reportai.runtime;

import java.util.List;

public record ExecutionResult(Status status, int exitCode, String stdout, String stderr, List<String> artifactKeys) {
    public enum Status {
        SUCCEEDED,
        TIMED_OUT,
        OUTPUT_LIMIT_EXCEEDED,
        ARTIFACT_LIMIT_EXCEEDED,
        REJECTED,
        FAILED
    }

    public ExecutionResult {
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
        artifactKeys = artifactKeys == null ? List.of() : List.copyOf(artifactKeys);
    }
}
