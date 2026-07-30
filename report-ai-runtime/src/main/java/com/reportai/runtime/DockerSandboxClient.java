package com.reportai.runtime;

import java.time.Duration;

/** Small port that keeps the domain independent of the Docker SDK and is easy to fake in tests. */
public interface DockerSandboxClient {
    String createContainer(SandboxContainerSpec specification);

    void startContainer(String containerId);

    SandboxExecution awaitAndCollect(String containerId, Duration timeout, long maxOutputBytes);

    void killContainer(String containerId);

    void removeContainer(String containerId);
}
