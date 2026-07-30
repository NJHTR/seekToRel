package com.reportai.runtime;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Docker Java SDK implementation. No shell or host process is ever started by ReportAI. */
public final class DockerJavaSandboxClient implements DockerSandboxClient {
    private final DockerClient docker;

    public DockerJavaSandboxClient(DockerClient docker) {
        this.docker = java.util.Objects.requireNonNull(docker, "docker");
    }

    public static DockerJavaSandboxClient connectFromEnvironment() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(30))
                .build();
        return new DockerJavaSandboxClient(DockerClientImpl.getInstance(config, httpClient));
    }

    @Override
    public String createContainer(SandboxContainerSpec specification) {
        HostConfig hostConfig = new HostConfig()
                .withBinds(new Bind(specification.hostWorkspace().toString(),
                        new Volume(specification.containerWorkspace()), AccessMode.rw))
                .withMemory(specification.memoryBytes())
                .withNanoCPUs(Math.multiplyExact(specification.cpuLimit(), 1_000_000_000L))
                .withPidsLimit(128L)
                .withReadonlyRootfs(true)
                .withSecurityOpts(List.of("no-new-privileges:true"));
        if (!specification.networkEnabled()) {
            hostConfig.withNetworkMode("none");
        }
        var command = docker.createContainerCmd(specification.image())
                .withHostConfig(hostConfig)
                .withWorkingDir(specification.containerWorkspace())
                .withCmd(specification.command())
                .withEnv(toDockerEnvironment(specification.environment()))
                .withTty(false)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();
        return command.getId();
    }

    @Override
    public void startContainer(String containerId) {
        docker.startContainerCmd(containerId).exec();
    }

    @Override
    public SandboxExecution awaitAndCollect(String containerId, Duration timeout, long maxOutputBytes) {
        BoundedLogs logs = new BoundedLogs(maxOutputBytes, () -> killContainer(containerId));
        ResultCallback.Adapter<Frame> logCallback = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Frame frame) {
                logs.accept(frame);
            }
        };
        docker.logContainerCmd(containerId).withStdOut(true).withStdErr(true).withFollowStream(true).exec(logCallback);

        CompletableFuture<Integer> exitCode = CompletableFuture.supplyAsync(() -> {
            WaitContainerResultCallback callback = new WaitContainerResultCallback();
            docker.waitContainerCmd(containerId).exec(callback);
            return callback.awaitStatusCode();
        });
        boolean timedOut = false;
        int code = -1;
        try {
            code = exitCode.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutException) {
            timedOut = true;
            killContainer(containerId);
            try {
                code = exitCode.get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                exitCode.cancel(true);
            }
        } catch (Exception failure) {
            throw new IllegalStateException("unable to wait for sandbox", failure);
        } finally {
            try {
                logCallback.awaitCompletion(Math.max(1, Math.min(timeout.toSeconds(), 5)), TimeUnit.SECONDS);
            } catch (Exception ignored) {
                try {
                    logCallback.close();
                } catch (Exception ignoredClose) {
                    // Best-effort close; the container is removed in the caller's finally block.
                }
            }
        }
        return new SandboxExecution(code, timedOut, logs.exceeded(), logs.stdout(), logs.stderr());
    }

    @Override
    public void killContainer(String containerId) {
        try {
            docker.killContainerCmd(containerId).exec();
        } catch (RuntimeException ignored) {
            // A container which already exited is safe to clean up.
        }
    }

    @Override
    public void removeContainer(String containerId) {
        docker.removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec();
    }

    private static List<String> toDockerEnvironment(java.util.Map<String, String> environment) {
        List<String> values = new ArrayList<>(environment.size());
        environment.forEach((key, value) -> values.add(key + "=" + value));
        return values;
    }

    private static final class BoundedLogs {
        private final long limit;
        private final Runnable onLimit;
        private final AtomicLong bytes = new AtomicLong();
        private final AtomicBoolean exceeded = new AtomicBoolean();
        private final StringBuilder stdout = new StringBuilder();
        private final StringBuilder stderr = new StringBuilder();

        private BoundedLogs(long limit, Runnable onLimit) {
            this.limit = limit;
            this.onLimit = onLimit;
        }

        private synchronized void accept(Frame frame) {
            byte[] payload = frame.getPayload() == null ? new byte[0] : frame.getPayload();
            long current = bytes.addAndGet(payload.length);
            if (current > limit) {
                if (exceeded.compareAndSet(false, true)) {
                    onLimit.run();
                }
                return;
            }
            String text = new String(payload, StandardCharsets.UTF_8);
            if (frame.getStreamType() == StreamType.STDERR) {
                stderr.append(text);
            } else {
                stdout.append(text);
            }
        }

        private boolean exceeded() { return exceeded.get(); }
        private synchronized String stdout() { return stdout.toString(); }
        private synchronized String stderr() { return stderr.toString(); }
    }
}
