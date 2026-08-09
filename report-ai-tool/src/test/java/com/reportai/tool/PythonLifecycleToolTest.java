package com.reportai.tool;

import com.reportai.common.tenant.TenantId;
import com.reportai.runtime.ExecutionRequest;
import com.reportai.runtime.ExecutionResult;
import com.reportai.runtime.RuntimeInstallResult;
import com.reportai.runtime.RuntimeLifecycleEngine;
import com.reportai.runtime.WorkspaceFileStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonLifecycleToolTest {
    private static final ToolContext CONTEXT = new ToolContext("task-1", TenantId.of("tenant-1"), "workspace-1");

    @Test
    void failedExecutionCleansVolumeAndRequestsInstallerReplay() {
        AtomicBoolean cleaned = new AtomicBoolean();
        RuntimeLifecycleEngine runtime = new RuntimeLifecycleEngine() {
            @Override public RuntimeInstallResult install(String workspace, ExecutionRequest request) {
                return new RuntimeInstallResult(new ExecutionResult(ExecutionResult.Status.SUCCEEDED, 0, "", "", List.of()), "volume-1");
            }
            @Override public ExecutionResult executeInstalled(String workspace, ExecutionRequest request, String volume) {
                assertEquals("volume-1", volume);
                return new ExecutionResult(ExecutionResult.Status.FAILED, 1, "", "ModuleNotFoundError", List.of());
            }
            @Override public void cleanup(String workspace, String volume) {
                assertEquals("volume-1", volume);
                cleaned.set(true);
            }
        };
        WorkspaceFileStore files = new WorkspaceFileStore() {
            @Override public void ensureWorkspace(String workspaceId) { }
            @Override public void writeUtf8(String workspaceId, String relativePath, String content) { }
        };
        PythonLifecycleTool tool = new PythonLifecycleTool("python.execute", PythonLifecycleTool.Operation.EXECUTE,
                runtime, files);

        ToolResult result = tool.execute(CONTEXT, new ToolInput(Map.of("entryPoint", "main.py",
                "source", "print(1)", "dependencyVolume", "volume-1")));

        assertEquals(ToolResult.Status.FAILED, result.status());
        assertTrue(cleaned.get());
        assertEquals(Boolean.TRUE, result.outputs().get("rewindToInstaller"));
        assertEquals(Boolean.TRUE, result.outputs().get("cleanupPerformed"));
        assertEquals("PYTHON_DEPENDENCY_MISSING", result.outputs().get("errorCode"));
    }

    @Test
    void explicitPrerequisiteFailureIsClassifiedAsMissingInput() {
        RuntimeLifecycleEngine runtime = new RuntimeLifecycleEngine() {
            @Override public RuntimeInstallResult install(String workspace, ExecutionRequest request) {
                return new RuntimeInstallResult(new ExecutionResult(ExecutionResult.Status.SUCCEEDED, 0, "", "", List.of()), "volume-1");
            }

            @Override public ExecutionResult executeInstalled(String workspace, ExecutionRequest request, String volume) {
                return new ExecutionResult(ExecutionResult.Status.FAILED, 2, "",
                        "前置条件失败：缺少经人工确认的 OCR 文字稿，未生成报告。", List.of());
            }

            @Override public void cleanup(String workspace, String volume) { }
        };
        WorkspaceFileStore files = new WorkspaceFileStore() {
            @Override public void ensureWorkspace(String workspaceId) { }
            @Override public void writeUtf8(String workspaceId, String relativePath, String content) { }
        };
        PythonLifecycleTool tool = new PythonLifecycleTool("python.execute", PythonLifecycleTool.Operation.EXECUTE,
                runtime, files);

        ToolResult result = tool.execute(CONTEXT, new ToolInput(Map.of("entryPoint", "main.py",
                "source", "raise SystemExit(2)", "dependencyVolume", "volume-1")));

        assertEquals(ToolResult.Status.FAILED, result.status());
        assertEquals("PYTHON_INPUT_MISSING", result.outputs().get("errorCode"));
    }

    @Test
    void runtimeFailureRequestsCoderRepairAfterCleanup() {
        RuntimeLifecycleEngine runtime = new RuntimeLifecycleEngine() {
            @Override public RuntimeInstallResult install(String workspace, ExecutionRequest request) {
                return new RuntimeInstallResult(new ExecutionResult(ExecutionResult.Status.SUCCEEDED, 0, "", "", List.of()), "volume-1");
            }

            @Override public ExecutionResult executeInstalled(String workspace, ExecutionRequest request, String volume) {
                return new ExecutionResult(ExecutionResult.Status.FAILED, 1, "",
                        "Traceback: ValueError: invalid DOCX XML parent", List.of());
            }

            @Override public void cleanup(String workspace, String volume) { }
        };
        WorkspaceFileStore files = new WorkspaceFileStore() {
            @Override public void ensureWorkspace(String workspaceId) { }
            @Override public void writeUtf8(String workspaceId, String relativePath, String content) { }
        };
        PythonLifecycleTool tool = new PythonLifecycleTool("python.execute", PythonLifecycleTool.Operation.EXECUTE,
                runtime, files);

        ToolResult result = tool.execute(CONTEXT, new ToolInput(Map.of("entryPoint", "main.py",
                "source", "raise ValueError()", "dependencyVolume", "volume-1")));

        assertEquals(ToolResult.Status.FAILED, result.status());
        assertEquals("PYTHON_RUNTIME_ERROR", result.outputs().get("errorCode"));
        assertEquals(Boolean.TRUE, result.outputs().get("rewindToCoder"));
    }

    @Test
    void prerequisiteMarkerOnStdoutIsAlsoClassifiedAsMissingInput() {
        RuntimeLifecycleEngine runtime = new RuntimeLifecycleEngine() {
            @Override public RuntimeInstallResult install(String workspace, ExecutionRequest request) {
                return new RuntimeInstallResult(new ExecutionResult(ExecutionResult.Status.SUCCEEDED, 0, "", "", List.of()), "volume-1");
            }

            @Override public ExecutionResult executeInstalled(String workspace, ExecutionRequest request, String volume) {
                return new ExecutionResult(ExecutionResult.Status.FAILED, 2,
                        "前置条件不满足：人工核验 OCR 文本缺失", "", List.of());
            }

            @Override public void cleanup(String workspace, String volume) { }
        };
        WorkspaceFileStore files = new WorkspaceFileStore() {
            @Override public void ensureWorkspace(String workspaceId) { }
            @Override public void writeUtf8(String workspaceId, String relativePath, String content) { }
        };
        PythonLifecycleTool tool = new PythonLifecycleTool("python.execute", PythonLifecycleTool.Operation.EXECUTE,
                runtime, files);

        ToolResult result = tool.execute(CONTEXT, new ToolInput(Map.of("entryPoint", "main.py",
                "source", "raise SystemExit(2)", "dependencyVolume", "volume-1")));

        assertEquals(ToolResult.Status.FAILED, result.status());
        assertEquals("PYTHON_INPUT_MISSING", result.outputs().get("errorCode"));
    }
}
