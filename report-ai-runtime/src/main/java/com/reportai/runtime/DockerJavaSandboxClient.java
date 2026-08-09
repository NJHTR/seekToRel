package com.reportai.runtime;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;

/** Docker Java SDK implementation. No shell or host process is ever started by ReportAI. */
public final class DockerJavaSandboxClient implements DockerSandboxClient {
    private static final String DOCKER_API_VERSION = "1.40";
    private static final long IMAGE_PULL_TIMEOUT_MINUTES = 10;
    private static final String SANDBOX_USER = "65532:65532";

    private final DockerClient docker;
    private final boolean allowImagePull;

    public DockerJavaSandboxClient(DockerClient docker) {
        this(docker, false);
    }

    public DockerJavaSandboxClient(DockerClient docker, boolean allowImagePull) {
        this.docker = java.util.Objects.requireNonNull(docker, "docker");
        this.allowImagePull = allowImagePull;
    }

    public static DockerJavaSandboxClient connectFromEnvironment() {
        return connectFromEnvironment(false);
    }

    public static DockerJavaSandboxClient connectFromEnvironment(boolean allowImagePull) {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withApiVersion(DOCKER_API_VERSION)
                .build();
        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(30))
                .build();
        return new DockerJavaSandboxClient(DockerClientImpl.getInstance(config, httpClient), allowImagePull);
    }

    @Override
    public String createContainer(SandboxContainerSpec specification) {
        return createContainer(specification, null);
    }

    @Override
    public String createContainer(SandboxContainerSpec specification, String dependencyVolume) {
        ensureImageAvailable(specification.image());
        Path outputWorkspace = specification.hostWorkspace().resolve("output").normalize();
        Path inputWorkspace = specification.hostWorkspace().resolve("input").normalize();
        HostConfig hostConfig = new HostConfig()
                .withBinds(specification.splitWorkspace()
                        ? new Bind(outputWorkspace.toString(), new Volume(specification.containerWorkspace()), AccessMode.rw)
                        : new Bind(specification.hostWorkspace().toString(),
                        new Volume(specification.containerWorkspace()), AccessMode.rw))
                .withMemory(specification.memoryBytes())
                .withNanoCPUs(Math.multiplyExact(specification.cpuLimit(), 1_000_000_000L))
                .withPidsLimit(128L)
                .withReadonlyRootfs(true)
                .withSecurityOpts(List.of("no-new-privileges:true"))
                .withTmpFs(Map.of("/tmp", specification.ephemeralDependencies()
                        ? "rw,nosuid,nodev,size=256m,uid=65532,gid=65532"
                        : "rw,nosuid,nodev,noexec,size=64m,uid=65532,gid=65532"));
        if (dependencyVolume != null && !dependencyVolume.isBlank()) {
            AccessMode dependencyMode = specification.dependencyVolumeWritable() ? AccessMode.rw : AccessMode.ro;
            if (specification.splitWorkspace()) {
                hostConfig.withBinds(
                        new Bind(outputWorkspace.toString(), new Volume(specification.containerWorkspace()), AccessMode.rw),
                        new Bind(inputWorkspace.toString(), new Volume(specification.containerWorkspace() + "/input"), AccessMode.ro),
                        new Bind(dependencyVolume, new Volume(PythonDependencyCommand.DEPENDENCY_VOLUME_TARGET), dependencyMode));
            } else {
                hostConfig.withBinds(
                        new Bind(specification.hostWorkspace().toString(),
                                new Volume(specification.containerWorkspace()), AccessMode.rw),
                        new Bind(dependencyVolume, new Volume(PythonDependencyCommand.DEPENDENCY_VOLUME_TARGET), dependencyMode));
            }
        } else if (specification.splitWorkspace()) {
            hostConfig.withBinds(
                    new Bind(outputWorkspace.toString(), new Volume(specification.containerWorkspace()), AccessMode.rw),
                    new Bind(inputWorkspace.toString(), new Volume(specification.containerWorkspace() + "/input"), AccessMode.ro));
        }
        if (!specification.networkEnabled()) {
            hostConfig.withNetworkMode("none");
        }
        var command = docker.createContainerCmd(specification.image())
                .withHostConfig(hostConfig)
                .withUser(SANDBOX_USER)
                .withCapDrop(Capability.ALL)
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
    public String createEphemeralVolume(String purpose) {
        String safePurpose = purpose == null ? "task" : purpose.replaceAll("[^A-Za-z0-9_.-]", "-");
        String name = "reportai-deps-" + safePurpose + "-" + UUID.randomUUID();
        return docker.createVolumeCmd().withName(name)
                .withLabels(Map.of("com.reportai.owner", "runtime", "com.reportai.ephemeral", "true"))
                .exec().getName();
    }

    @Override
    public void removeEphemeralVolume(String volumeName) {
        if (volumeName == null || volumeName.isBlank()) return;
        try {
            docker.removeVolumeCmd(volumeName).exec();
        } catch (RuntimeException ignored) {
            // A reconciler can remove an orphaned labeled volume later.
        }
    }

    /** Removes labeled dependency volumes which have no container attachment. */
    public int reconcileOrphanedEphemeralVolumes() {
        int removed = 0;
        var response = docker.listVolumesCmd()
                .withFilter("label", List.of("com.reportai.ephemeral=true"))
                .exec();
        if (response.getVolumes() == null) return 0;
        for (var volume : response.getVolumes()) {
            String name = volume.getName();
            if (name == null || name.isBlank()) continue;
            boolean attached = !docker.listContainersCmd().withShowAll(true)
                    .withVolumeFilter(List.of(name)).exec().isEmpty();
            if (!attached) {
                try {
                    docker.removeVolumeCmd(name).exec();
                    removed++;
                } catch (RuntimeException ignored) {
                    // Another worker may have claimed/removed it concurrently.
                }
            }
        }
        return removed;
    }

    private void ensureImageAvailable(String image) {
        try {
            docker.inspectImageCmd(image).exec();
            return;
        } catch (NotFoundException missingImage) {
            if (!allowImagePull) {
                throw new IllegalStateException("runtime image is not preloaded: " + image);
            }
            // Pull only allow-listed, digest-pinned images supplied by RuntimeConfiguration.
        }

        ImageReference reference = ImageReference.parse(image);
        PullImageCmd pull = docker.pullImageCmd(reference.repository());
        if (reference.tag() != null) {
            pull.withTag(reference.tag());
        }
        PullImageResultCallback callback = new PullImageResultCallback();
        try {
            if (!pull.exec(callback).awaitCompletion(IMAGE_PULL_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                throw new IllegalStateException("runtime image pull timed out");
            }
            docker.inspectImageCmd(image).exec();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("runtime image pull interrupted", interrupted);
        }
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

    private record ImageReference(String repository, String tag) {
        private static ImageReference parse(String image) {
            int digestSeparator = image.indexOf('@');
            if (digestSeparator > 0) {
                // Docker Engine accepts the digest in fromImage directly. Do not
                // reinterpret it as a mutable tag when the image is missing.
                return new ImageReference(image, null);
            }
            int separator = image.lastIndexOf(':');
            int pathSeparator = image.lastIndexOf('/');
            if (separator <= pathSeparator) {
                return new ImageReference(image, "latest");
            }
            return new ImageReference(image.substring(0, separator), image.substring(separator + 1));
        }
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
