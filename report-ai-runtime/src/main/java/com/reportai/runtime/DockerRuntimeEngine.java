package com.reportai.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Runtime application service: validates the request, delegates to Docker, and collects bounded artifacts. */
public final class DockerRuntimeEngine implements RuntimeEngine, RuntimeLifecycleEngine {
    private static final Pattern WORKSPACE_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Pattern ENTRY_POINT = Pattern.compile("[A-Za-z0-9_./-]{1,256}");
    /** Reserved workspace entries used only by the Installer lifecycle. */
    private static final String DEPENDENCY_DIRECTORY = ".reportai-deps";
    private static final String REQUIREMENTS_FILE = ".reportai-requirements.txt";
    private static final Pattern ENV_KEY = Pattern.compile("[A-Z_][A-Z0-9_]{0,63}");
    /** Generated programs must never receive credentials through the process environment. */
    private static final Pattern SENSITIVE_ENV_KEY = Pattern.compile(
            "(?i).*(^|_)(TOKEN|PASSWORD|PASSWD|SECRET|API_KEY|ACCESS_KEY|PRIVATE_KEY|CREDENTIALS?)(_|$).*");
    private static final Set<String> BLOCKED_ENV = Set.of(
            "LD_PRELOAD", "LD_LIBRARY_PATH", "PYTHONPATH", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS",
            "CLASSPATH", "PATH", "BASH_ENV", "ENV", "DOCKER_HOST");
    private final DockerSandboxClient client;
    private final RuntimeConfiguration configuration;

    public DockerRuntimeEngine(DockerSandboxClient client, RuntimeConfiguration configuration) {
        this.client = java.util.Objects.requireNonNull(client, "client");
        this.configuration = java.util.Objects.requireNonNull(configuration, "configuration");
    }

    @Override
    public ExecutionResult execute(String workspaceId, ExecutionRequest request) {
        try {
            ValidatedRequest validated = validate(workspaceId, request);
            Set<Path> before = snapshot(validated.outputWorkspace());
            if (!request.dependencies().isEmpty()) {
                RuntimeInstallResult installed = installValidated(validated, request);
                if (!installed.succeeded()) {
                    cleanup(workspaceId, installed.dependencyVolume());
                    return installed.result();
                }
                try {
                    return executeInstalledValidated(validated, request, installed.dependencyVolume(), before);
                } finally {
                    cleanup(workspaceId, installed.dependencyVolume());
                }
            }
            SandboxContainerSpec spec = new SandboxContainerSpec(
                    validated.runtime().image(), validated.runtime().command(validated.entryPoint()),
                    validated.environment(), validated.workspace(), "/workspace",
                    request.policy().cpuLimit(), request.policy().memoryBytes(), request.policy().networkEnabled(), false, false, true);
            SandboxExecution result = runContainer(spec, request.policy());
            ArtifactCollection artifacts = collectArtifacts(validated.outputWorkspace(), before, "output/");
            return result(result, artifacts);
        } catch (RejectedRequest rejected) {
            return new ExecutionResult(ExecutionResult.Status.REJECTED, -1, "", rejected.getMessage(), List.of());
        } catch (RuntimeException failure) {
            return new ExecutionResult(ExecutionResult.Status.FAILED, -1, "", "sandbox execution failed", List.of());
        }
    }

    /** Creates the installer volume and runs only the installer container. */
    @Override
    public RuntimeInstallResult install(String workspaceId, ExecutionRequest request) {
        try {
            ValidatedRequest validated = validate(workspaceId, request);
            if (!"python".equalsIgnoreCase(validated.runtime().language())) {
                throw new RejectedRequest("dependency lifecycle is only supported for Python");
            }
            if (request.dependencies().isEmpty()) {
                return new RuntimeInstallResult(new ExecutionResult(ExecutionResult.Status.SUCCEEDED, 0,
                        "", "", List.of()), null);
            }
            return installValidated(validated, request);
        } catch (RejectedRequest rejected) {
            return new RuntimeInstallResult(new ExecutionResult(ExecutionResult.Status.REJECTED, -1,
                    "", rejected.getMessage(), List.of()), null);
        } catch (RuntimeException failure) {
            return new RuntimeInstallResult(new ExecutionResult(ExecutionResult.Status.FAILED, -1,
                    "", "sandbox installer failed", List.of()), null);
        }
    }

    /** Executes against an already prepared dependency volume. */
    @Override
    public ExecutionResult executeInstalled(String workspaceId, ExecutionRequest request,
                                            String dependencyVolume) {
        try {
            ValidatedRequest validated = validate(workspaceId, request);
            if (!"python".equalsIgnoreCase(validated.runtime().language())) {
                throw new RejectedRequest("dependency lifecycle is only supported for Python");
            }
            // Legacy/fake Docker clients may not implement named volumes and
            // intentionally return null; the real Docker adapter always does.
            if (!request.dependencies().isEmpty() && dependencyVolume != null && !validVolume(dependencyVolume)) {
                throw new RejectedRequest("dependency volume is required");
            }
            Set<Path> before = snapshot(validated.outputWorkspace());
            return executeInstalledValidated(validated, request, dependencyVolume, before);
        } catch (RejectedRequest rejected) {
            return new ExecutionResult(ExecutionResult.Status.REJECTED, -1,
                    "", rejected.getMessage(), List.of());
        } catch (RuntimeException failure) {
            return new ExecutionResult(ExecutionResult.Status.FAILED, -1,
                    "", "sandbox execution failed", List.of());
        }
    }

    /** Idempotent cleanup of task-owned dependency state. */
    @Override
    public void cleanup(String workspaceId, String dependencyVolume) {
        if (workspaceId == null || !WORKSPACE_ID.matcher(workspaceId).matches()) {
            throw new IllegalArgumentException("workspaceId is invalid");
        }
        Path workspace = configuration.workspaceRoot().resolve(workspaceId).normalize();
        if (!workspace.startsWith(configuration.workspaceRoot()) || !Files.isDirectory(workspace)
                || Files.isSymbolicLink(workspace)) {
            throw new IllegalArgumentException("workspace does not exist");
        }
        RuntimeException cleanupFailure = null;
        try {
            cleanupDependencyWorkspace(workspace);
        } catch (RuntimeException failure) {
            cleanupFailure = failure;
        } finally {
            if (dependencyVolume != null && !dependencyVolume.isBlank()) {
                if (!validVolume(dependencyVolume)) {
                    if (cleanupFailure == null) cleanupFailure = new IllegalArgumentException("dependency volume is invalid");
                } else {
                    client.removeEphemeralVolume(dependencyVolume);
                }
            }
        }
        if (cleanupFailure != null) throw cleanupFailure;
    }

    private RuntimeInstallResult installValidated(ValidatedRequest validated, ExecutionRequest request) {
        if (!"python".equalsIgnoreCase(validated.runtime().language())) {
            throw new RejectedRequest("dependency installer is only supported for Python");
        }
        String dependencyVolume = client.createEphemeralVolume(validated.workspace().getFileName().toString());
        if (dependencyVolume != null && !validVolume(dependencyVolume)) {
            throw new RejectedRequest("dependency volume could not be created");
        }
        SandboxContainerSpec installer = new SandboxContainerSpec(
                validated.runtime().image(), PythonDependencyCommand.buildInstaller(request.dependencies()),
                validated.environment(), validated.workspace(), "/workspace",
                request.policy().cpuLimit(), request.policy().memoryBytes(), false, true, true, true);
        SandboxExecution installResult;
        try {
            installResult = runContainer(installer, request.policy(), dependencyVolume);
        } catch (RuntimeException failure) {
            try {
                cleanupDependencyWorkspace(validated.workspace());
            } finally {
                client.removeEphemeralVolume(dependencyVolume);
            }
            throw failure;
        }
        ExecutionResult result = result(installResult, new ArtifactCollection(List.of(), false));
        if (result.status() != ExecutionResult.Status.SUCCEEDED) {
            cleanup(validated.workspace().getFileName().toString(), dependencyVolume);
            return new RuntimeInstallResult(result, null);
        }
        return new RuntimeInstallResult(result, dependencyVolume);
    }

    private ExecutionResult executeInstalledValidated(ValidatedRequest validated, ExecutionRequest request,
                                                      String dependencyVolume, Set<Path> before) {
        SandboxContainerSpec execution = new SandboxContainerSpec(
                validated.runtime().image(), PythonDependencyCommand.buildExecution(validated.entryPoint()),
                validated.environment(), validated.workspace(), "/workspace",
                request.policy().cpuLimit(), request.policy().memoryBytes(), false, true, false, true);
        SandboxExecution executionResult = runContainer(execution, request.policy(), dependencyVolume);
        ArtifactCollection artifacts = collectArtifacts(validated.outputWorkspace(), before, "output/");
        return result(executionResult, artifacts);
    }

    private static boolean validVolume(String volume) {
        return volume != null && volume.length() <= 256 && volume.matches("[A-Za-z0-9_.-]+");
    }

    private SandboxExecution runContainer(SandboxContainerSpec spec, ExecutionPolicy policy) {
        return runContainer(spec, policy, null);
    }

    private SandboxExecution runContainer(SandboxContainerSpec spec, ExecutionPolicy policy, String dependencyVolume) {
        String containerId = client.createContainer(spec, dependencyVolume);
        boolean started = false;
        try {
            client.startContainer(containerId);
            started = true;
            return client.awaitAndCollect(containerId, policy.timeout(), policy.maxOutputBytes());
        } finally {
            if (started) {
                client.killContainer(containerId);
            }
            client.removeContainer(containerId);
        }
    }

    private static ExecutionResult result(SandboxExecution execution, ArtifactCollection artifacts) {
        ExecutionResult.Status status = execution.timedOut() ? ExecutionResult.Status.TIMED_OUT
                : execution.outputLimitExceeded() ? ExecutionResult.Status.OUTPUT_LIMIT_EXCEEDED
                : artifacts.limitExceeded() ? ExecutionResult.Status.ARTIFACT_LIMIT_EXCEEDED
                : execution.exitCode() == 0 ? ExecutionResult.Status.SUCCEEDED : ExecutionResult.Status.FAILED;
        return new ExecutionResult(status, execution.exitCode(), execution.stdout(), execution.stderr(), artifacts.keys());
    }

    private static void cleanupDependencyWorkspace(Path workspace) {
        deleteTree(workspace.resolve(".reportai-deps"), workspace);
        deleteTree(workspace.resolve(".reportai-requirements.txt"), workspace);
        Path output = workspace.resolve("output").normalize();
        if (Files.isDirectory(output, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            deleteTree(output.resolve(".reportai-deps"), output);
            deleteTree(output.resolve(".reportai-requirements.txt"), output);
        }
    }

    private static void deleteTree(Path target, Path workspace) {
        Path root = workspace.toAbsolutePath().normalize();
        Path safe = target.toAbsolutePath().normalize();
        if (!safe.startsWith(root) || safe.equals(root)) {
            throw new RejectedRequest("dependency cleanup path is invalid");
        }
        try {
            if (!Files.exists(safe) && !Files.isSymbolicLink(safe)) return;
            try (var paths = Files.walk(safe)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (IOException failure) { throw new CleanupFailure(failure); }
                });
            }
        } catch (CleanupFailure failure) {
            throw new RejectedRequest("dependency cleanup failed");
        } catch (IOException failure) {
            throw new RejectedRequest("dependency cleanup failed");
        }
    }

    private ValidatedRequest validate(String workspaceId, ExecutionRequest request) {
        if (workspaceId == null || !WORKSPACE_ID.matcher(workspaceId).matches()) {
            throw new RejectedRequest("workspaceId is invalid");
        }
        if (request == null) {
            throw new RejectedRequest("execution request is required");
        }
        LanguageRuntime runtime = configuration.language(request.language());
        if (runtime == null) {
            throw new RejectedRequest("language is not enabled");
        }
        String entryPoint = safeRelativePath(request.entryPoint());
        validateEntryPointExtension(runtime.language(), entryPoint);
        Path workspace = configuration.workspaceRoot().resolve(workspaceId).normalize();
        if (!workspace.startsWith(configuration.workspaceRoot()) || !Files.isDirectory(workspace)) {
            throw new RejectedRequest("workspace does not exist");
        }
        if (Files.isSymbolicLink(workspace)) {
            throw new RejectedRequest("symbolic-link workspace is not allowed");
        }
        ensureNoSymlinks(workspace);
        try {
            Files.createDirectories(workspace.resolve("input"));
            Files.createDirectories(workspace.resolve("output"));
            ensureNoSymlinks(workspace.resolve("input"));
            ensureNoSymlinks(workspace.resolve("output"));
        } catch (IOException failure) {
            throw new RejectedRequest("workspace split directories cannot be prepared");
        }
        ExecutionPolicy policy = request.policy();
        if (policy.networkEnabled()) {
            throw new RejectedRequest("network access requires the managed egress policy");
        }
        if (policy.timeout().compareTo(configuration.maxTimeout()) > 0
                || policy.maxOutputBytes() > configuration.maxOutputBytes()
                || policy.cpuLimit() > configuration.maxCpuLimit()
                || policy.memoryBytes() > configuration.maxMemoryBytes()) {
            throw new RejectedRequest("execution limits exceed platform policy");
        }
        Map<String, String> env = validateEnvironment(request.environment());
        if ("python".equalsIgnoreCase(runtime.language())) {
            try {
                configuration.validatePythonDependencies(request.dependencies());
            } catch (IllegalArgumentException invalid) {
                throw new RejectedRequest(invalid.getMessage());
            }
        } else if (!request.dependencies().isEmpty()) {
            throw new RejectedRequest("dependencies are only supported for Python");
        }
        return new ValidatedRequest(runtime, entryPoint, workspace, workspace.resolve("output"), env);
    }

    private static String safeRelativePath(String value) {
        if (value == null || value.isBlank() || value.length() > 256 || value.indexOf('\\') >= 0
                || value.indexOf('\0') >= 0 || !ENTRY_POINT.matcher(value).matches()) {
            throw new RejectedRequest("entryPoint is invalid");
        }
        Path path;
        try {
            path = Paths.get(value).normalize();
        } catch (InvalidPathException invalidPath) {
            throw new RejectedRequest("entryPoint is invalid");
        }
        if (path.isAbsolute()) {
            throw new RejectedRequest("entryPoint is invalid");
        }
        if (path.startsWith("..") || path.toString().isBlank()) {
            throw new RejectedRequest("entryPoint escapes workspace");
        }
        return path.toString().replace('\\', '/');
    }

    private static void validateEntryPointExtension(String language, String entryPoint) {
        String lower = entryPoint.toLowerCase(Locale.ROOT);
        boolean valid = switch (language.toLowerCase(Locale.ROOT)) {
            case "python" -> lower.endsWith(".py");
            case "java" -> lower.endsWith(".java");
            case "cpp" -> lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".cxx");
            default -> false;
        };
        if (!valid) {
            throw new RejectedRequest("entryPoint extension does not match language");
        }
    }

    private static Map<String, String> validateEnvironment(Map<String, String> environment) {
        if (environment == null || environment.size() > 64) {
            throw new RejectedRequest("environment is too large");
        }
        Map<String, String> safe = new HashMap<>();
        environment.forEach((key, value) -> {
            if (key == null || value == null || !ENV_KEY.matcher(key).matches() || BLOCKED_ENV.contains(key)
                    || SENSITIVE_ENV_KEY.matcher(key).matches()
                    || value.length() > 4096 || value.indexOf('\0') >= 0) {
                throw new RejectedRequest("environment variable is invalid");
            }
            safe.put(key, value);
        });
        return Map.copyOf(safe);
    }

    private static Set<Path> snapshot(Path workspace) {
        Set<Path> files = new HashSet<>();
        try (var paths = Files.walk(workspace, 8)) {
            paths.filter(path -> !Files.isSymbolicLink(path) && Files.isRegularFile(path)).forEach(files::add);
            return files;
        } catch (IOException failure) {
            throw new RejectedRequest("workspace cannot be inspected");
        }
    }

    private static void ensureNoSymlinks(Path workspace) {
        try (var paths = Files.walk(workspace, 8)) {
            if (paths.anyMatch(Files::isSymbolicLink)) {
                throw new RejectedRequest("symbolic links in workspace are not allowed");
            }
        } catch (IOException failure) {
            throw new RejectedRequest("workspace cannot be inspected");
        }
    }

    private ArtifactCollection collectArtifacts(Path workspace, Set<Path> before, String logicalPrefix) {
        List<String> keys = new java.util.ArrayList<>();
        boolean exceeded = false;
        try (var paths = Files.walk(workspace, 8)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (before.contains(path) || Files.isSymbolicLink(path)) {
                    continue;
                }
                long size = Files.size(path);
                if (size > configuration.maxArtifactBytes()) {
                    exceeded = true;
                    continue;
                }
                Path relative = workspace.relativize(path).normalize();
                if (relative.startsWith("..")) {
                    exceeded = true;
                    continue;
                }
                // Installer bookkeeping is temporary implementation state, not
                // a user artifact.  It is removed in the finally block after
                // collection; exposing it here would return artifact keys that
                // no longer exist and break report reproducibility.
                if (relative.getNameCount() > 0
                        && (DEPENDENCY_DIRECTORY.equals(relative.getName(0).toString())
                        || (relative.getNameCount() == 1
                        && REQUIREMENTS_FILE.equals(relative.getFileName().toString())))) {
                    continue;
                }
                if (keys.size() >= configuration.maxArtifacts()) {
                    exceeded = true;
                    break;
                }
                String logical = relative.toString().replace('\\', '/');
                keys.add((logicalPrefix == null ? "" : logicalPrefix) + logical);
            }
            return new ArtifactCollection(keys, exceeded);
        } catch (IOException failure) {
            return new ArtifactCollection(keys, true);
        }
    }

    private record ValidatedRequest(LanguageRuntime runtime, String entryPoint, Path workspace,
                                    Path outputWorkspace, Map<String, String> environment) { }

    private record ArtifactCollection(List<String> keys, boolean limitExceeded) {
        private ArtifactCollection {
            keys = List.copyOf(keys);
        }
    }

    private static final class RejectedRequest extends RuntimeException {
        private RejectedRequest(String message) { super(message); }
    }

    private static final class CleanupFailure extends RuntimeException {
        private CleanupFailure(Throwable cause) { super(cause); }
    }
}
