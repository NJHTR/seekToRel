package com.reportai.tool;

import org.junit.jupiter.api.Test;
import com.reportai.runtime.ExecutionResult;
import com.reportai.runtime.RuntimeEngine;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultToolRegistryTest {
    @Test
    void normalizesLookupsAndRejectsUnsafeNames() {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        Tool tool = new Tool() {
            @Override
            public String name() {
                return "python.run";
            }

            @Override
            public ToolResult execute(ToolContext context, ToolInput input) {
                return new ToolResult(ToolResult.Status.SUCCEEDED, Map.of(), "ok");
            }
        };
        registry.register(tool);

        assertEquals(tool, registry.find(" PYTHON.RUN ").orElseThrow());
        assertEquals(java.util.Set.of("python.run"), registry.names());
        assertThrows(IllegalStateException.class, () -> registry.register(tool));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new NamedTool("Run Tool")));
    }

    @Test
    void builtInRegistryContainsOnlySandboxBackedTools() {
        RuntimeEngine runtime = (workspace, request) -> new ExecutionResult(
                ExecutionResult.Status.SUCCEEDED, 0, "", "", java.util.List.of());
        ToolRegistry registry = BuiltInToolRegistry.create(runtime);

        assertEquals(java.util.Set.of("python.run", "java.run", "chart.generate"), registry.names());
    }

    private record NamedTool(String name) implements Tool {
        @Override
        public ToolResult execute(ToolContext context, ToolInput input) {
            return new ToolResult(ToolResult.Status.SUCCEEDED, Map.of(), "ok");
        }
    }
}
