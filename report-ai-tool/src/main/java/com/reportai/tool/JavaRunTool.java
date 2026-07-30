package com.reportai.tool;

import com.reportai.runtime.ExecutionPolicy;
import com.reportai.runtime.ExecutionRequest;
import com.reportai.runtime.ExecutionResult;
import com.reportai.runtime.RuntimeEngine;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Runs a Java source entry point through the sandbox port.
 *
 * <p>The tool intentionally accepts a workspace-relative source path only. It never accepts
 * source text, a class path, a JVM option, or an arbitrary command. Runtime image and command
 * selection remain the responsibility of {@link RuntimeEngine}.</p>
 */
public final class JavaRunTool implements Tool {
    public static final String NAME = "java.run";
    private static final Pattern ENTRY_POINT = Pattern.compile("[A-Za-z0-9_./-]{1,256}");
    private static final Pattern ENV_KEY = Pattern.compile("[A-Z_][A-Z0-9_]{0,63}");
    private static final Set<String> BLOCKED_ENVIRONMENT = Set.of(
            "LD_PRELOAD", "LD_LIBRARY_PATH", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH",
            "PATH", "BASH_ENV", "ENV", "DOCKER_HOST");
    private static final Set<String> ALLOWED_ARGUMENTS = Set.of(
            "entryPoint", "environment", "timeoutMs", "maxOutputBytes", "cpuLimit", "memoryBytes",
            "networkEnabled");
    private final RuntimeEngine runtime;

    public JavaRunTool(RuntimeEngine runtime) {
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
            String entryPoint = requiredEntryPoint(arguments.get("entryPoint"));
            Map<String, String> environment = environment(arguments.get("environment"));
            if (booleanArgument(arguments, "networkEnabled", false)) {
                return rejected("network access is not permitted for Java tools");
            }
            ExecutionPolicy policy = policy(arguments);
            ExecutionResult result = runtime.execute(context.workspaceId(),
                    new ExecutionRequest("java", entryPoint, environment, policy));
            return toToolResult(result);
        } catch (InvalidArguments invalid) {
            return rejected(invalid.getMessage());
        } catch (RuntimeException failure) {
            // Do not expose runtime/container details to an agent or an HTTP caller.
            return new ToolResult(ToolResult.Status.FAILED, Map.of(), "Java sandbox execution failed");
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
        String message = status == ToolResult.Status.SUCCEEDED ? "Java execution completed"
                : status == ToolResult.Status.REJECTED ? "Java execution request was rejected"
                : "Java execution failed";
        return new ToolResult(status, outputs, message);
    }

    private static String requiredEntryPoint(Object raw) {
        if (!(raw instanceof String value) || value.isBlank() || value.length() > 256
                || !ENTRY_POINT.matcher(value).matches() || value.contains("..")
                || value.startsWith("/")
                || !value.toLowerCase(Locale.ROOT).endsWith(".java")) {
            throw new InvalidArguments("entryPoint must be a safe workspace-relative .java path");
        }
        return value;
    }

    private static ExecutionPolicy policy(Map<String, Object> arguments) {
        long timeoutMs = longArgument(arguments, "timeoutMs", 120_000L, 1L, 86_400_000L);
        long maxOutputBytes = longArgument(arguments, "maxOutputBytes", 2 * 1024 * 1024L, 1L,
                512L * 1024 * 1024);
        int cpuLimit = (int) longArgument(arguments, "cpuLimit", 1L, 1L, 256L);
        long memoryBytes = longArgument(arguments, "memoryBytes", 512L * 1024 * 1024L, 1L,
                64L * 1024 * 1024 * 1024);
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
            if (!(key instanceof String stringKey) || !(value instanceof String stringValue)
                    || !ENV_KEY.matcher(stringKey).matches() || BLOCKED_ENVIRONMENT.contains(stringKey)
                    || stringValue.length() > 4096 || stringValue.indexOf('\0') >= 0) {
                throw new InvalidArguments("environment variable is invalid");
            }
            result.put(stringKey, stringValue);
        });
        return Map.copyOf(result);
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

    private static long longArgument(Map<String, Object> arguments, String key, long defaultValue,
                                     long min, long max) {
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
            throw new InvalidArguments("unsupported Java tool argument");
        }
    }

    private static ToolResult rejected(String message) {
        return new ToolResult(ToolResult.Status.REJECTED, Map.of(), message);
    }

    private static final class InvalidArguments extends RuntimeException {
        private InvalidArguments(String message) {
            super(message);
        }
    }
}
