package com.reportai.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Executes a chart-producing Python program and only exposes bounded image artifacts.
 * Rendering remains in the runtime container; this class is the policy boundary for
 * chart output and therefore does not parse or execute user code on the host.
 */
public final class ChartTool implements Tool {
    public static final String NAME = "chart.generate";
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".svg", ".jpg", ".jpeg", ".webp");
    private final PythonRunTool python;

    public ChartTool(PythonRunTool python) {
        this.python = Objects.requireNonNull(python, "python");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolResult execute(ToolContext context, ToolInput input) {
        String prefix = prefix(input);
        if (prefix == null) {
            return new ToolResult(ToolResult.Status.REJECTED, Map.of(), "artifactPrefix is invalid");
        }
        ToolResult execution = python.execute(context, withoutChartOnlyArguments(input));
        if (execution.status() != ToolResult.Status.SUCCEEDED) {
            return execution;
        }
        Object rawArtifacts = execution.outputs().get("artifactKeys");
        if (!(rawArtifacts instanceof List<?> artifacts)) {
            return failed(execution, "Python execution returned no chart artifacts");
        }
        List<String> charts = new ArrayList<>();
        for (Object artifact : artifacts) {
            if (artifact instanceof String key && isChart(key, prefix)) {
                charts.add(key);
            }
        }
        if (charts.isEmpty()) {
            return failed(execution, "Python execution succeeded but produced no chart image");
        }
        Map<String, Object> outputs = new HashMap<>(execution.outputs());
        outputs.put("chartArtifacts", List.copyOf(charts));
        outputs.put("chartCount", charts.size());
        return new ToolResult(ToolResult.Status.SUCCEEDED, outputs, "Chart generation completed");
    }

    private static boolean isChart(String key, String prefix) {
        if (key.isBlank() || key.length() > 512 || key.contains("\\") || key.contains("..")) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return (prefix.isEmpty() || key.startsWith(prefix))
                && IMAGE_EXTENSIONS.stream().anyMatch(normalized::endsWith);
    }

    private static String prefix(ToolInput input) {
        if (input == null || input.arguments().get("artifactPrefix") == null) {
            return "";
        }
        Object value = input.arguments().get("artifactPrefix");
        if (!(value instanceof String string) || string.length() > 256 || string.contains("\\")
                || string.contains("..") || string.startsWith("/")) {
            return null;
        }
        return string;
    }

    private static ToolInput withoutChartOnlyArguments(ToolInput input) {
        if (input == null || !input.arguments().containsKey("artifactPrefix")) {
            return input;
        }
        Map<String, Object> arguments = new HashMap<>(input.arguments());
        arguments.remove("artifactPrefix");
        return new ToolInput(arguments);
    }

    private static ToolResult failed(ToolResult execution, String message) {
        Map<String, Object> outputs = new HashMap<>(execution.outputs());
        outputs.put("chartArtifacts", List.of());
        outputs.put("chartCount", 0);
        return new ToolResult(ToolResult.Status.FAILED, outputs, message);
    }
}
