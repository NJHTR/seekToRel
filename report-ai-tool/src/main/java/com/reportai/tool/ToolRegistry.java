package com.reportai.tool;

import java.util.Set;
import java.util.Optional;

public interface ToolRegistry {
    void register(Tool tool);

    Optional<Tool> find(String name);

    default Tool require(String name) {
        return find(name).orElseThrow(() -> new IllegalArgumentException("tool is not registered: " + name));
    }

    default Set<String> names() {
        return Set.of();
    }
}
