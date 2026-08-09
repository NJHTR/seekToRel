package com.reportai.tool;

import com.reportai.runtime.ExecutionPolicy;
import com.reportai.runtime.ExecutionRequest;
import com.reportai.runtime.ExecutionResult;
import com.reportai.runtime.DependencySpec;
import com.reportai.runtime.PythonDependencyRequest;
import com.reportai.runtime.RuntimeEngine;
import com.reportai.runtime.WorkspaceFileStore;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Runs a Python entry point through the sandbox port. When a generated source string is
 * present, it is materialized through the bounded workspace-file port; callers never
 * provide a host path and the tool never starts a host process itself.
 */
public final class PythonRunTool implements Tool {
    public static final String NAME = "python.run";
    private static final Set<String> ALLOWED_ARGUMENTS = Set.of(
            "entryPoint", "environment", "timeoutMs", "maxOutputBytes", "cpuLimit", "memoryBytes",
            "networkEnabled", "source", "dependencies", "resolvedDependencies");
    private final RuntimeEngine runtime;
    private final WorkspaceFileStore workspaceFiles;

    public PythonRunTool(RuntimeEngine runtime) {
        this(runtime, null);
    }

    public PythonRunTool(RuntimeEngine runtime, WorkspaceFileStore workspaceFiles) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.workspaceFiles = workspaceFiles;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolResult execute(ToolContext context, ToolInput input) {
        try {
            validateContext(context);
            Map<String, Object> arguments = input == null ? Map.of() : input.arguments();
            rejectUnknownArguments(arguments);
            String entryPoint = requiredString(arguments, "entryPoint");
            Map<String, String> environment = environment(arguments.get("environment"));
            boolean networkEnabled = booleanArgument(arguments, "networkEnabled", false);
            if (networkEnabled) {
                return rejected("network access is not permitted for Python tools", Map.of());
            }
            ExecutionPolicy policy = policy(arguments);
            java.util.List<String> dependencies = dependencies(arguments.get("dependencies"));
            java.util.List<DependencySpec> resolvedDependencies = resolvedDependencies(arguments.get("resolvedDependencies"));
            if (!resolvedDependencies.isEmpty() && dependencies.isEmpty()) {
                dependencies = resolvedDependencies.stream()
                        .map(value -> value.name() + "==" + value.version()).toList();
            }
            if (workspaceFiles != null) {
                workspaceFiles.ensureWorkspace(context.workspaceId());
                String source = optionalSource(arguments.get("source"));
                if (source != null) {
                    workspaceFiles.writeUtf8(context.workspaceId(), entryPoint, source);
                }
            } else if (arguments.containsKey("source")) {
                return rejected("source materialization is not configured", Map.of());
            }
            ExecutionResult result = runtime.execute(context.workspaceId(),
                    new ExecutionRequest("python", entryPoint, environment, policy,
                            new PythonDependencyRequest(dependencies, resolvedDependencies)));
            return toToolResult(result);
        } catch (InvalidArguments invalid) {
            return rejected(invalid.getMessage(), Map.of());
        } catch (RuntimeException failure) {
            return new ToolResult(ToolResult.Status.FAILED, Map.of(), "Python sandbox execution failed");
        }
    }

    private static ToolResult toToolResult(ExecutionResult result) {
        ToolResult.Status status = switch (result.status()) {
            case SUCCEEDED -> ToolResult.Status.SUCCEEDED;
            case REJECTED -> ToolResult.Status.REJECTED;
            default -> ToolResult.Status.FAILED;
        };
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("status", result.status().name());
        outputs.put("exitCode", result.exitCode());
        outputs.put("stdout", result.stdout());
        outputs.put("stderr", result.stderr());
        outputs.put("artifactKeys", result.artifactKeys());
        String errorCode = classifyFailure(result);
        if (errorCode != null) {
            outputs.put("errorCode", errorCode);
        }
        String message = status == ToolResult.Status.SUCCEEDED ? "Python execution completed"
                : status == ToolResult.Status.REJECTED ? "Python execution request was rejected"
                : "Python execution failed (" + errorCode + ")";
        return new ToolResult(status, outputs, message);
    }

    private static String classifyFailure(ExecutionResult result) {
        return PythonFailureClassifier.classify(result);
    }

    private static ExecutionPolicy policy(Map<String, Object> arguments) {
        long timeoutMs = longArgument(arguments, "timeoutMs", 120_000L, 1L, 86_400_000L);
        long maxOutputBytes = longArgument(arguments, "maxOutputBytes", 2 * 1024 * 1024L, 1L, 512L * 1024 * 1024);
        int cpuLimit = (int) longArgument(arguments, "cpuLimit", 1L, 1L, 256L);
        long memoryBytes = longArgument(arguments, "memoryBytes", 512L * 1024 * 1024, 1L, 64L * 1024 * 1024 * 1024);
        return new ExecutionPolicy(Duration.ofMillis(timeoutMs), maxOutputBytes, cpuLimit, memoryBytes, false);
    }

    private static Map<String, String> environment(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> values) || values.size() > 64) {
            throw new InvalidArguments("environment must be an object with at most 64 entries");
        }
        Map<String, String> result = new HashMap<>();
        values.forEach((key, value) -> {
            if (!(key instanceof String stringKey) || !(value instanceof String stringValue)) {
                throw new InvalidArguments("environment keys and values must be strings");
            }
            result.put(stringKey, stringValue);
        });
        return Map.copyOf(result);
    }

    private static String requiredString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (!(value instanceof String string) || string.isBlank() || string.length() > 256) {
            throw new InvalidArguments(key + " must be a non-blank string");
        }
        return string;
    }

    private static String optionalSource(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String source) || source.isBlank() || source.length() > 512 * 1024) {
            throw new InvalidArguments("source must be a non-blank string within the permitted size");
        }
        return source;
    }

    private static java.util.List<String> dependencies(Object raw) {
        if (raw == null) {
            return java.util.List.of();
        }
        if (!(raw instanceof java.util.List<?> values) || values.size() > 16) {
            throw new InvalidArguments("dependencies must be an array with at most 16 entries");
        }
        java.util.List<String> result = new java.util.ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof String dependency) || dependency.isBlank()) {
                throw new InvalidArguments("dependencies must contain non-blank strings");
            }
            result.add(dependency);
        }
        try {
            return new com.reportai.runtime.PythonDependencyRequest(result).packages();
        } catch (IllegalArgumentException invalid) {
            throw new InvalidArguments(invalid.getMessage());
        }
    }

    private static java.util.List<DependencySpec> resolvedDependencies(Object raw) {
        if (raw == null) {
            return java.util.List.of();
        }
        if (!(raw instanceof java.util.List<?> values) || values.size() > 16) {
            throw new InvalidArguments("resolvedDependencies must be an array with at most 16 entries");
        }
        java.util.List<DependencySpec> result = new java.util.ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> dependency)) {
                throw new InvalidArguments("resolvedDependencies must contain objects");
            }
            Object name = dependency.get("name");
            Object version = dependency.get("version");
            Object sha256 = dependency.get("sha256");
            if (!(name instanceof String) || !(version instanceof String) || !(sha256 instanceof String)) {
                throw new InvalidArguments("resolved dependency requires name, version and sha256");
            }
            try {
                result.add(new DependencySpec((String) name, (String) version, (String) sha256));
            } catch (IllegalArgumentException invalid) {
                throw new InvalidArguments(invalid.getMessage());
            }
        }
        return java.util.List.copyOf(result);
    }

    private static boolean booleanArgument(Map<String, Object> arguments, String key, boolean defaultValue) {
        Object value = arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Boolean bool)) {
            throw new InvalidArguments(key + " must be boolean");
        }
        return bool;
    }

    private static long longArgument(Map<String, Object> arguments, String key, long defaultValue, long min, long max) {
        Object value = arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Number number) || number instanceof Float || number instanceof Double
                || number.longValue() != number.doubleValue()) {
            throw new InvalidArguments(key + " must be an integer");
        }
        long result = number.longValue();
        if (result < min || result > max) {
            throw new InvalidArguments(key + " is outside the permitted range");
        }
        return result;
    }

    private static void validateContext(ToolContext context) {
        if (context == null || context.tenantId() == null || context.taskId() == null || context.taskId().isBlank()
                || context.workspaceId() == null || context.workspaceId().isBlank()) {
            throw new InvalidArguments("authenticated task context is required");
        }
    }

    private static void rejectUnknownArguments(Map<String, Object> arguments) {
        if (!ALLOWED_ARGUMENTS.containsAll(arguments.keySet())) {
            throw new InvalidArguments("unsupported Python tool argument");
        }
    }

    private static ToolResult rejected(String message, Map<String, Object> outputs) {
        return new ToolResult(ToolResult.Status.REJECTED, outputs, message);
    }

    private static final class InvalidArguments extends RuntimeException {
        private InvalidArguments(String message) {
            super(message);
        }
    }
}
