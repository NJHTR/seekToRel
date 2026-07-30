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
public final class DockerRuntimeEngine implements RuntimeEngine {
    private static final Pattern WORKSPACE_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Pattern ENTRY_POINT = Pattern.compile("[A-Za-z0-9_./-]{1,256}");
    private static final Pattern ENV_KEY = Pattern.compile("[A-Z_][A-Z0-9_]{0,63}");
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
            Set<Path> before = snapshot(validated.workspace());
            SandboxContainerSpec spec = new SandboxContainerSpec(
                    validated.runtime().image(), validated.runtime().command(validated.entryPoint()),
                    validated.environment(), validated.workspace(), "/workspace",
                    request.policy().cpuLimit(), request.policy().memoryBytes(), request.policy().networkEnabled());
            String containerId = client.createContainer(spec);
            boolean started = false;
            try {
                client.startContainer(containerId);
                started = true;
                SandboxExecution result = client.awaitAndCollect(containerId, request.policy().timeout(),
                        request.policy().maxOutputBytes());
                ArtifactCollection artifacts = collectArtifacts(validated.workspace(), before);
                ExecutionResult.Status status = result.timedOut() ? ExecutionResult.Status.TIMED_OUT
                        : result.outputLimitExceeded() ? ExecutionResult.Status.OUTPUT_LIMIT_EXCEEDED
                        : artifacts.limitExceeded() ? ExecutionResult.Status.ARTIFACT_LIMIT_EXCEEDED
                        : result.exitCode() == 0 ? ExecutionResult.Status.SUCCEEDED : ExecutionResult.Status.FAILED;
                return new ExecutionResult(status, result.exitCode(), result.stdout(), result.stderr(), artifacts.keys());
            } finally {
                if (started) {
                    client.killContainer(containerId);
                }
                client.removeContainer(containerId);
            }
        } catch (RejectedRequest rejected) {
            return new ExecutionResult(ExecutionResult.Status.REJECTED, -1, "", rejected.getMessage(), List.of());
        } catch (RuntimeException failure) {
            return new ExecutionResult(ExecutionResult.Status.FAILED, -1, "", "sandbox execution failed", List.of());
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
        ExecutionPolicy policy = request.policy();
        if (policy.timeout().compareTo(configuration.maxTimeout()) > 0
                || policy.maxOutputBytes() > configuration.maxOutputBytes()
                || policy.cpuLimit() > configuration.maxCpuLimit()
                || policy.memoryBytes() > configuration.maxMemoryBytes()) {
            throw new RejectedRequest("execution limits exceed platform policy");
        }
        Map<String, String> env = validateEnvironment(request.environment());
        return new ValidatedRequest(runtime, entryPoint, workspace, env);
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

    private ArtifactCollection collectArtifacts(Path workspace, Set<Path> before) {
        List<String> keys = new java.util.ArrayList<>();
        boolean exceeded = false;
        try (var paths = Files.walk(workspace, 8)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (before.contains(path) || Files.isSymbolicLink(path)) {
                    continue;
                }
                if (keys.size() >= configuration.maxArtifacts()) {
                    exceeded = true;
                    break;
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
                keys.add(relative.toString().replace('\\', '/'));
            }
            return new ArtifactCollection(keys, exceeded);
        } catch (IOException failure) {
            return new ArtifactCollection(keys, true);
        }
    }

    private record ValidatedRequest(LanguageRuntime runtime, String entryPoint, Path workspace,
                                    Map<String, String> environment) { }

    private record ArtifactCollection(List<String> keys, boolean limitExceeded) {
        private ArtifactCollection {
            keys = List.copyOf(keys);
        }
    }

    private static final class RejectedRequest extends RuntimeException {
        private RejectedRequest(String message) { super(message); }
    }
}
