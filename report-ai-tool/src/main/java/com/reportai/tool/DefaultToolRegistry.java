package com.reportai.tool;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

public final class DefaultToolRegistry implements ToolRegistry {
    private static final Pattern TOOL_NAME = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    private final ConcurrentMap<String, Tool> tools = new ConcurrentHashMap<>();

    @Override
    public void register(Tool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("tool and tool name are required");
        }
        String name = tool.name();
        if (name == null || name.length() > 96 || !TOOL_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("tool name must be a lowercase token: " + name);
        }
        Tool previous = tools.putIfAbsent(name, tool);
        if (previous != null) {
            throw new IllegalStateException("duplicate tool: " + name);
        }
    }

    @Override
    public Optional<Tool> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String normalized = name.trim().toLowerCase(java.util.Locale.ROOT);
        return TOOL_NAME.matcher(normalized).matches() ? Optional.ofNullable(tools.get(normalized)) : Optional.empty();
    }

    @Override
    public Set<String> names() {
        return Set.copyOf(tools.keySet());
    }
}
