package com.reportai.runtime;

public interface RuntimeEngine {
    ExecutionResult execute(String workspaceId, ExecutionRequest request);
}
