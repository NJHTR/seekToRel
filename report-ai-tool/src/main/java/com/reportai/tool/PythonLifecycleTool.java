package com.reportai.tool;

import com.reportai.runtime.DependencySpec;
import com.reportai.runtime.ExecutionPolicy;
import com.reportai.runtime.ExecutionRequest;
import com.reportai.runtime.ExecutionResult;
import com.reportai.runtime.PythonDependencyRequest;
import com.reportai.runtime.RuntimeLifecycleEngine;
import com.reportai.runtime.RuntimeInstallResult;
import com.reportai.runtime.WorkspaceFileStore;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Checkpoint-friendly Python lifecycle tools.  Each operation maps to one
 * deterministic runtime boundary; the agent pipeline persists its output
 * before advancing to the next operation.
 */
public final class PythonLifecycleTool implements Tool {
    public enum Operation { INSTALL, EXECUTE, CLEANUP }

    private final String name;
    private final Operation operation;
    private final RuntimeLifecycleEngine lifecycle;
    private final WorkspaceFileStore workspaceFiles;

    public PythonLifecycleTool(String name, Operation operation, RuntimeLifecycleEngine lifecycle,
                               WorkspaceFileStore workspaceFiles) {
        this.name = Objects.requireNonNull(name, "name");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.workspaceFiles = workspaceFiles;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ToolResult execute(ToolContext context, ToolInput input) {
        try {
            validateContext(context);
            Map<String, Object> arguments = input == null ? Map.of() : input.arguments();
            return switch (operation) {
                case INSTALL -> install(context, arguments);
                case EXECUTE -> execute(context, arguments);
                case CLEANUP -> cleanup(context, arguments);
            };
        } catch (InvalidArguments invalid) {
            return rejected(invalid.getMessage(), Map.of());
        } catch (RuntimeException failure) {
            return new ToolResult(ToolResult.Status.FAILED, Map.of(),
                    "Python lifecycle operation failed");
        }
    }

    private ToolResult install(ToolContext context, Map<String, Object> args) {
        RequestParts parts = request(args, false);
        RuntimeInstallResult installed = lifecycle.install(context.workspaceId(), parts.request());
        Map<String, Object> outputs = executionOutputs(installed.result());
        if (installed.dependencyVolume() != null) outputs.put("dependencyVolume", installed.dependencyVolume());
        return result(installed.result(), outputs, "Python dependencies installed");
    }

    private ToolResult execute(ToolContext context, Map<String, Object> args) {
        RequestParts parts = request(args, true);
        if (workspaceFiles == null) throw new InvalidArguments("source materialization is not configured");
        String source = parts.source();
        if (source != null) workspaceFiles.writeUtf8Output(context.workspaceId(), parts.request().entryPoint(), source);
        String volume = string(args, "dependencyVolume", false);
        ExecutionResult result = lifecycle.executeInstalled(context.workspaceId(), parts.request(), volume);
        Map<String, Object> outputs = executionOutputs(result);
        if (result.status() != ExecutionResult.Status.SUCCEEDED) {
            // A failed execution must not strand a task-scoped volume.  The
            // checkpoint is rewound by the agent executor, so the installer
            // will provision a fresh volume on the next delivery.
            try {
                lifecycle.cleanup(context.workspaceId(), volume);
                outputs.put("cleanupPerformed", true);
                outputs.put("rewindToInstaller", true);
            } catch (RuntimeException cleanupFailure) {
                outputs.put("cleanupPerformed", false);
                outputs.put("cleanupError", "runtime cleanup failed");
            }
            String errorCode = classifyFailure(result);
            if ("PYTHON_RUNTIME_ERROR".equals(errorCode)
                    || "PYTHON_ARGUMENT_ERROR".equals(errorCode)) {
                // A deterministic interpreter failure is feedback for the
                // generated program, not a reason to replay the same bytes.
                // The pipeline will rewind to Coder while retaining the
                // bounded stderr summary and immutable environment contract.
                outputs.put("rewindToCoder", true);
            }
        }
        return result(result, outputs, "Python execution completed");
    }

    private ToolResult cleanup(ToolContext context, Map<String, Object> args) {
        String volume = string(args, "dependencyVolume", false);
        lifecycle.cleanup(context.workspaceId(), volume);
        return new ToolResult(ToolResult.Status.SUCCEEDED,
                Map.of("status", "SUCCEEDED", "cleaned", true), "Python runtime cleaned up");
    }

    private static ToolResult result(ExecutionResult result, Map<String, Object> outputs, String successMessage) {
        ToolResult.Status status = result.status() == ExecutionResult.Status.SUCCEEDED
                ? ToolResult.Status.SUCCEEDED
                : result.status() == ExecutionResult.Status.REJECTED
                ? ToolResult.Status.REJECTED : ToolResult.Status.FAILED;
        String error = classifyFailure(result);
        if (error != null) outputs.put("errorCode", error);
        String message = status == ToolResult.Status.SUCCEEDED ? successMessage
                : status == ToolResult.Status.REJECTED ? "Python lifecycle request was rejected"
                : "Python lifecycle operation failed (" + error + ")";
        return new ToolResult(status, outputs, message);
    }

    private static Map<String, Object> executionOutputs(ExecutionResult result) {
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("status", result.status().name());
        outputs.put("exitCode", result.exitCode());
        outputs.put("stdout", result.stdout());
        outputs.put("stderr", result.stderr());
        outputs.put("artifactKeys", result.artifactKeys());
        return outputs;
    }

    private static RequestParts request(Map<String, Object> args, boolean includeSource) {
        rejectUnknown(args);
        String entry = string(args, "entryPoint", true);
        Map<String, String> environment = environment(args.get("environment"));
        List<String> dependencies = strings(args.get("dependencies"), "dependencies");
        List<DependencySpec> resolved = resolved(args.get("resolvedDependencies"));
        if (!resolved.isEmpty() && dependencies.isEmpty()) {
            dependencies = resolved.stream().map(d -> d.name() + "==" + d.version()).toList();
        }
        ExecutionPolicy policy = policy(args);
        String source = includeSource ? optionalSource(args.get("source")) : null;
        return new RequestParts(new ExecutionRequest("python", entry, environment, policy,
                new PythonDependencyRequest(dependencies, resolved)), source);
    }

    private record RequestParts(ExecutionRequest request, String source) { }

    private static final java.util.Set<String> ALLOWED = java.util.Set.of(
            "entryPoint", "environment", "timeoutMs", "maxOutputBytes", "cpuLimit", "memoryBytes",
            "networkEnabled", "source", "dependencies", "resolvedDependencies", "dependencyVolume");

    private static void rejectUnknown(Map<String, Object> args) {
        if (!ALLOWED.containsAll(args.keySet())) throw new InvalidArguments("unsupported Python lifecycle argument");
    }

    private static String string(Map<String, Object> args, String key, boolean required) {
        Object value = args.get(key);
        if (value == null && !required) return null;
        if (!(value instanceof String text) || text.isBlank() || text.length() > 256) {
            throw new InvalidArguments(key + " must be a non-blank string");
        }
        return text;
    }

    private static String optionalSource(Object value) {
        if (value == null) return null;
        if (!(value instanceof String source) || source.isBlank() || source.length() > 512 * 1024) {
            throw new InvalidArguments("source must be a non-blank string within the permitted size");
        }
        return source;
    }

    private static Map<String, String> environment(Object raw) {
        if (raw == null) return Map.of();
        if (!(raw instanceof Map<?, ?> values) || values.size() > 64) {
            throw new InvalidArguments("environment must be an object");
        }
        Map<String, String> result = new HashMap<>();
        values.forEach((key, value) -> {
            if (!(key instanceof String k) || !(value instanceof String v)) throw new InvalidArguments("environment must contain strings");
            result.put(k, v);
        });
        return Map.copyOf(result);
    }

    private static List<String> strings(Object raw, String field) {
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> values) || values.size() > 16) throw new InvalidArguments(field + " must be an array");
        return values.stream().map(value -> {
            if (!(value instanceof String s) || s.isBlank()) throw new InvalidArguments(field + " contains an invalid value");
            return s;
        }).toList();
    }

    private static List<DependencySpec> resolved(Object raw) {
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> values) || values.size() > 16) throw new InvalidArguments("resolvedDependencies must be an array");
        return values.stream().map(value -> {
            if (!(value instanceof Map<?, ?> map) || !(map.get("name") instanceof String name)
                    || !(map.get("version") instanceof String version) || !(map.get("sha256") instanceof String hash)) {
                throw new InvalidArguments("resolved dependency requires name, version and sha256");
            }
            return new DependencySpec(name, version, hash);
        }).toList();
    }

    private static ExecutionPolicy policy(Map<String, Object> args) {
        long timeout = number(args, "timeoutMs", 120_000L, 1L, 86_400_000L);
        long output = number(args, "maxOutputBytes", 2 * 1024 * 1024L, 1L, 512L * 1024 * 1024);
        int cpu = (int) number(args, "cpuLimit", 1L, 1L, 256L);
        long memory = number(args, "memoryBytes", 512L * 1024 * 1024, 1L, 64L * 1024 * 1024 * 1024);
        Object network = args.get("networkEnabled");
        if (Boolean.TRUE.equals(network)) throw new InvalidArguments("network access is not permitted");
        return new ExecutionPolicy(Duration.ofMillis(timeout), output, cpu, memory, false);
    }

    private static long number(Map<String, Object> args, String key, long fallback, long min, long max) {
        Object value = args.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number number) || number.longValue() != number.doubleValue()) throw new InvalidArguments(key + " must be an integer");
        long result = number.longValue();
        if (result < min || result > max) throw new InvalidArguments(key + " is outside the permitted range");
        return result;
    }

    private static String classifyFailure(ExecutionResult result) {
        return PythonFailureClassifier.classify(result);
    }

    private static void validateContext(ToolContext context) {
        if (context == null || context.tenantId() == null || context.taskId() == null || context.taskId().isBlank()
                || context.workspaceId() == null || context.workspaceId().isBlank()) throw new InvalidArguments("authenticated task context is required");
    }

    private static ToolResult rejected(String message, Map<String, Object> outputs) {
        return new ToolResult(ToolResult.Status.REJECTED, outputs, message);
    }

    private static final class InvalidArguments extends RuntimeException {
        private InvalidArguments(String message) { super(message); }
    }
}
