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
import static org.junit.jupiter.api.Assertions.assertFalse;

class JavaRunToolTest {
    private static final ToolContext CONTEXT = new ToolContext("task-1", TenantId.of("tenant-1"), "workspace-1");

    @Test
    void delegatesOnlySafeJavaRequestToRuntime() {
        AtomicReference<ExecutionRequest> request = new AtomicReference<>();
        RuntimeEngine runtime = (workspace, value) -> {
            assertEquals("workspace-1", workspace);
            request.set(value);
            return new ExecutionResult(ExecutionResult.Status.SUCCEEDED, 0, "ok", "", List.of("result.txt"));
        };

        ToolResult result = new JavaRunTool(runtime).execute(CONTEXT,
                new ToolInput(Map.of("entryPoint", "src/Main.java", "timeoutMs", 2_000L,
                        "environment", Map.of("REPORTAI_RUN", "1"))));

        assertEquals(ToolResult.Status.SUCCEEDED, result.status());
        assertEquals("java", request.get().language());
        assertEquals("src/Main.java", request.get().entryPoint());
        assertEquals(2_000L, request.get().policy().timeout().toMillis());
        assertFalse(request.get().policy().networkEnabled());
    }

    @Test
    void rejectsNetworkUnknownAndUnsafeArgumentsBeforeRuntime() {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        RuntimeEngine runtime = (workspace, value) -> {
            called.set(true);
            return new ExecutionResult(ExecutionResult.Status.SUCCEEDED, 0, "", "", List.of());
        };
        JavaRunTool tool = new JavaRunTool(runtime);

        ToolResult network = tool.execute(CONTEXT,
                new ToolInput(Map.of("entryPoint", "Main.java", "networkEnabled", true)));
        ToolResult unknown = tool.execute(CONTEXT,
                new ToolInput(Map.of("entryPoint", "Main.java", "jvmOptions", "-Xmx1g")));
        ToolResult traversal = tool.execute(CONTEXT,
                new ToolInput(Map.of("entryPoint", "../Main.java")));
        ToolResult commandInjection = tool.execute(CONTEXT,
                new ToolInput(Map.of("entryPoint", "Main.java;touch-pwned.java")));
        ToolResult blockedEnvironment = tool.execute(CONTEXT,
                new ToolInput(Map.of("entryPoint", "Main.java", "environment", Map.of("PATH", "/tmp"))));

        assertEquals(ToolResult.Status.REJECTED, network.status());
        assertEquals(ToolResult.Status.REJECTED, unknown.status());
        assertEquals(ToolResult.Status.REJECTED, traversal.status());
        assertEquals(ToolResult.Status.REJECTED, commandInjection.status());
        assertEquals(ToolResult.Status.REJECTED, blockedEnvironment.status());
        assertEquals(false, called.get());
    }

    @Test
    void rejectsInvalidContextAndNumericLimits() {
        RuntimeEngine runtime = (workspace, value) -> new ExecutionResult(
                ExecutionResult.Status.SUCCEEDED, 0, "", "", List.of());
        JavaRunTool tool = new JavaRunTool(runtime);

        ToolResult noContext = tool.execute(null, new ToolInput(Map.of("entryPoint", "Main.java")));
        ToolResult badTimeout = tool.execute(CONTEXT,
                new ToolInput(Map.of("entryPoint", "Main.java", "timeoutMs", -1L)));
        ToolResult fractional = tool.execute(CONTEXT,
                new ToolInput(Map.of("entryPoint", "Main.java", "timeoutMs", 1.5d)));

        assertEquals(ToolResult.Status.REJECTED, noContext.status());
        assertEquals(ToolResult.Status.REJECTED, badTimeout.status());
        assertEquals(ToolResult.Status.REJECTED, fractional.status());
    }
}
