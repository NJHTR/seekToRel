package com.reportai.runtime;

/**
 * Durable lifecycle boundary for dependency installation and execution.
 *
 * <p>The ordinary {@link RuntimeEngine#execute(String, ExecutionRequest)} API
 * remains available for one-shot callers.  Task orchestration uses this port so
 * installer, execution and cleanup can be committed as independent checkpoint
 * steps.  The dependency volume is an opaque, task-scoped Docker handle; it is
 * never interpreted as a host path.</p>
 */
public interface RuntimeLifecycleEngine {
    RuntimeInstallResult install(String workspaceId, ExecutionRequest request);

    ExecutionResult executeInstalled(String workspaceId, ExecutionRequest request,
                                     String dependencyVolume);

    void cleanup(String workspaceId, String dependencyVolume);
}
