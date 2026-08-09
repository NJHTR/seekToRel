package com.reportai.runtime;

import java.time.Duration;

/** Small port that keeps the domain independent of the Docker SDK and is easy to fake in tests. */
public interface DockerSandboxClient {
    String createContainer(SandboxContainerSpec specification);

    /** Creates a task-scoped volume for installer output; null means legacy fake/client mode. */
    default String createEphemeralVolume(String purpose) {
        return null;
    }

    /** Mounts the task-scoped dependency volume when supported by the client. */
    default String createContainer(SandboxContainerSpec specification, String dependencyVolume) {
        return createContainer(specification);
    }

    void startContainer(String containerId);

    SandboxExecution awaitAndCollect(String containerId, Duration timeout, long maxOutputBytes);

    void killContainer(String containerId);

    void removeContainer(String containerId);

    default void removeEphemeralVolume(String volumeName) {
        // Legacy ports have no volume lifecycle; real Docker clients override this.
    }
}
