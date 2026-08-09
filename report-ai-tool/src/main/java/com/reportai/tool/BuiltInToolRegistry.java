package com.reportai.tool;

import com.reportai.runtime.RuntimeEngine;
import com.reportai.runtime.RuntimeLifecycleEngine;
import com.reportai.runtime.WorkspaceFileStore;

import java.util.Objects;

/** Factory for the tools which are safe to expose in the default agent profile. */
public final class BuiltInToolRegistry {
    private BuiltInToolRegistry() {
    }

    public static ToolRegistry create(RuntimeEngine runtime) {
        return create(runtime, null);
    }

    public static ToolRegistry create(RuntimeEngine runtime, WorkspaceFileStore workspaceFiles) {
        Objects.requireNonNull(runtime, "runtime");
        DefaultToolRegistry registry = new DefaultToolRegistry();
        PythonRunTool python = new PythonRunTool(runtime, workspaceFiles);
        registry.register(python);
        registry.register(new JavaRunTool(runtime));
        registry.register(new ChartTool(python));
        if (runtime instanceof RuntimeLifecycleEngine lifecycle) {
            registry.register(new PythonLifecycleTool("python.install", PythonLifecycleTool.Operation.INSTALL,
                    lifecycle, workspaceFiles));
            registry.register(new PythonLifecycleTool("python.execute", PythonLifecycleTool.Operation.EXECUTE,
                    lifecycle, workspaceFiles));
            registry.register(new PythonLifecycleTool("python.cleanup", PythonLifecycleTool.Operation.CLEANUP,
                    lifecycle, workspaceFiles));
        }
        return registry;
    }
}
