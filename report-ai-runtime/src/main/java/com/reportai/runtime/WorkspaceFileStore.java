package com.reportai.runtime;

/**
 * Controlled file preparation boundary for an isolated runtime workspace.
 * Implementations must never accept host paths supplied by a caller.
 */
public interface WorkspaceFileStore {
    void ensureWorkspace(String workspaceId);

    void writeUtf8(String workspaceId, String relativePath, String content);

    default void writeUtf8Output(String workspaceId, String relativePath, String content) {
        writeUtf8(workspaceId, relativePath, content);
    }
}
