package com.reportai.agent;

import com.reportai.common.tenant.TenantId;
import com.reportai.tool.DefaultToolRegistry;
import com.reportai.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ToolBackedAgentTest {
    @Test
    void keepsBoundedExitCodeAndRedactedRuntimeDiagnostic() {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.register(new com.reportai.tool.Tool() {
            @Override
            public String name() {
                return "python.execute";
            }

            @Override
            public ToolResult execute(com.reportai.tool.ToolContext context,
                                      com.reportai.tool.ToolInput input) {
                return new ToolResult(ToolResult.Status.FAILED,
                        Map.of("errorCode", "PYTHON_INPUT_MISSING", "exitCode", 2,
                                "stderr", "前置条件失败 password=secret-value"),
                        "Python lifecycle operation failed (PYTHON_INPUT_MISSING)");
            }
        });

        AgentResult result = new ToolBackedAgent("execution", "python.execute",
                "runtimeRequest", "execution").execute(new AgentContext("task-1",
                TenantId.of("tenant-1"), Map.of("workspaceId", "workspace-1",
                "runtimeRequest", Map.of("entryPoint", "main.py")), null, registry));

        assertEquals(AgentResult.Status.FAILED, result.status());
        assertEquals("PYTHON_INPUT_MISSING", result.outputs().get("toolErrorCode"));
        assertEquals(2L, result.outputs().get("toolExitCode"));
        String diagnostic = String.valueOf(result.outputs().get("toolStderrSummary"));
        assertFalse(diagnostic.contains("secret-value"));
        assertEquals("前置条件失败 password=[REDACTED]", diagnostic);
    }
}
