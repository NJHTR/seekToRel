package com.reportai.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** SDK-neutral description of a sandbox container. */
public record SandboxContainerSpec(
        String image,
        List<String> command,
        Map<String, String> environment,
        Path hostWorkspace,
        String containerWorkspace,
        int cpuLimit,
        long memoryBytes,
        boolean networkEnabled,
        boolean ephemeralDependencies,
        boolean dependencyVolumeWritable,
        boolean splitWorkspace) {
    public SandboxContainerSpec(String image, List<String> command, Map<String, String> environment,
                                Path hostWorkspace, String containerWorkspace, int cpuLimit,
                                long memoryBytes, boolean networkEnabled) {
        this(image, command, environment, hostWorkspace, containerWorkspace, cpuLimit, memoryBytes,
                networkEnabled, false, false);
    }

    public SandboxContainerSpec(String image, List<String> command, Map<String, String> environment,
                                Path hostWorkspace, String containerWorkspace, int cpuLimit,
                                long memoryBytes, boolean networkEnabled, boolean ephemeralDependencies) {
        this(image, command, environment, hostWorkspace, containerWorkspace, cpuLimit, memoryBytes,
                networkEnabled, ephemeralDependencies, false);
    }
    public SandboxContainerSpec(String image, List<String> command, Map<String, String> environment,
                                Path hostWorkspace, String containerWorkspace, int cpuLimit,
                                long memoryBytes, boolean networkEnabled, boolean ephemeralDependencies,
                                boolean dependencyVolumeWritable) {
        this(image, command, environment, hostWorkspace, containerWorkspace, cpuLimit, memoryBytes,
                networkEnabled, ephemeralDependencies, dependencyVolumeWritable, false);
    }
    public SandboxContainerSpec {
        if (image == null || image.isBlank() || command == null || command.isEmpty()
                || hostWorkspace == null || containerWorkspace == null || containerWorkspace.isBlank()
                || cpuLimit <= 0 || memoryBytes <= 0) {
            throw new IllegalArgumentException("invalid sandbox container specification");
        }
        if (networkEnabled) {
            throw new IllegalArgumentException("unmanaged sandbox network access is not supported");
        }
        command = List.copyOf(command);
        environment = environment == null ? Map.of() : Map.copyOf(environment);
        hostWorkspace = hostWorkspace.toAbsolutePath().normalize();
    }
}
