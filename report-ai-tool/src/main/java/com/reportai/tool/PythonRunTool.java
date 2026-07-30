package com.reportai.tool;

import com.reportai.runtime.ExecutionPolicy;
import com.reportai.runtime.ExecutionRequest;
import com.reportai.runtime.ExecutionResult;
import com.reportai.runtime.RuntimeEngine;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Runs an existing Python entry point through the sandbox port. The tool never starts a
 * process itself and deliberately does not accept source code or a host path.
 */
public final class PythonRunTool implements Tool {
    public static final String NAME = "python.run";
    private static final Set<String> ALLOWED_ARGUMENTS = Set.of(
            "entryPoint", "environment", "timeoutMs", "maxOutputBytes", "cpuLimit", "memoryBytes",
            "networkEnabled");
    private final RuntimeEngine runtime;

    public PythonRunTool(RuntimeEngine runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
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
            ExecutionResult result = runtime.execute(context.workspaceId(),
                    new ExecutionRequest("python", entryPoint, environment, policy));
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
        String message = status == ToolResult.Status.SUCCEEDED ? "Python execution completed"
                : status == ToolResult.Status.REJECTED ? "Python execution request was rejected"
                : "Python execution failed";
        return new ToolResult(status, outputs, message);
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
