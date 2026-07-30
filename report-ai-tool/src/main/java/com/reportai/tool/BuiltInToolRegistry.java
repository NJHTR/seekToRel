package com.reportai.tool;

import com.reportai.runtime.RuntimeEngine;

import java.util.Objects;

/** Factory for the tools which are safe to expose in the default agent profile. */
public final class BuiltInToolRegistry {
    private BuiltInToolRegistry() {
    }

    public static ToolRegistry create(RuntimeEngine runtime) {
        Objects.requireNonNull(runtime, "runtime");
        DefaultToolRegistry registry = new DefaultToolRegistry();
        PythonRunTool python = new PythonRunTool(runtime);
        registry.register(python);
        registry.register(new JavaRunTool(runtime));
        registry.register(new ChartTool(python));
        return registry;
    }
}
