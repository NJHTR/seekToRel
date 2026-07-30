package com.reportai.tool;

import com.reportai.common.tenant.TenantId;
import com.reportai.runtime.ExecutionRequest;
import com.reportai.runtime.ExecutionResult;
import com.reportai.runtime.RuntimeEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonRunToolTest {
    private static final ToolContext CONTEXT = new ToolContext("task-1", TenantId.of("tenant-1"), "workspace-1");

    @Test
    void delegatesOnlySafePythonRequestToRuntime() {
        AtomicReference<ExecutionRequest> request = new AtomicReference<>();
        RuntimeEngine runtime = (workspace, value) -> {
            assertEquals("workspace-1", workspace);
            request.set(value);
            return new ExecutionResult(ExecutionResult.Status.SUCCEEDED, 0, "ok", "", List.of("plot.png"));
        };

        ToolResult result = new PythonRunTool(runtime).execute(CONTEXT,
                new ToolInput(Map.of("entryPoint", "scripts/run.py", "timeoutMs", 2_000L)));

        assertEquals(ToolResult.Status.SUCCEEDED, result.status());
        assertEquals("python", request.get().language());
        assertEquals("scripts/run.py", request.get().entryPoint());
        assertTrue(request.get().policy().timeout().toMillis() == 2_000L);
    }

    @Test
    void rejectsNetworkAndUnknownArgumentsBeforeRuntime() {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        RuntimeEngine runtime = (workspace, value) -> {
            called.set(true);
            return new ExecutionResult(ExecutionResult.Status.SUCCEEDED, 0, "", "", List.of());
        };
        PythonRunTool tool = new PythonRunTool(runtime);

        ToolResult network = tool.execute(CONTEXT,
                new ToolInput(Map.of("entryPoint", "run.py", "networkEnabled", true)));
        ToolResult unknown = tool.execute(CONTEXT,
                new ToolInput(Map.of("entryPoint", "run.py", "source", "print(1)")));

        assertEquals(ToolResult.Status.REJECTED, network.status());
        assertEquals(ToolResult.Status.REJECTED, unknown.status());
        assertEquals(false, called.get());
    }

    @Test
    void chartToolRequiresImageArtifactAndFiltersUnsafeKeys() {
        RuntimeEngine runtime = (workspace, value) -> new ExecutionResult(
                ExecutionResult.Status.SUCCEEDED, 0, "", "", List.of("plot.png", "notes.txt", "../escape.svg"));
        ToolResult result = new ChartTool(new PythonRunTool(runtime)).execute(CONTEXT,
                new ToolInput(Map.of("entryPoint", "run.py", "artifactPrefix", "plot")));

        assertEquals(ToolResult.Status.SUCCEEDED, result.status());
        assertEquals(List.of("plot.png"), result.outputs().get("chartArtifacts"));
        assertEquals(1, result.outputs().get("chartCount"));
    }
}
