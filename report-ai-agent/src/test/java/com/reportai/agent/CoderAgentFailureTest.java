package com.reportai.agent;

import com.reportai.common.tenant.TenantId;
import com.reportai.llm.ChatRequest;
import com.reportai.llm.ChatResponse;
import com.reportai.llm.LlmException;
import com.reportai.llm.LlmProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoderAgentFailureTest {
    @Test
    void exposesStableTimeoutCodeWithoutProviderDetails() {
        LlmProvider provider = new LlmProvider() {
            @Override
            public String providerId() {
                return "test";
            }

            @Override
            public ChatResponse chat(ChatRequest request) {
                throw new LlmException("LLM_TIMEOUT", "upstream https://secret.example timed out");
            }
        };
        AgentContext context = new AgentContext("task-1", TenantId.of("tenant-a"),
                Map.of("plan", "print a result", "language", "python"), provider, null);

        AgentResult result = new CoderAgent().execute(context);

        assertEquals(AgentResult.Status.FAILED, result.status());
        assertEquals("LLM execution failed for agent: coder (LLM_TIMEOUT)", result.message());
        assertFalse(result.message().contains("secret.example"));
    }

    @Test
    void appliesConfiguredCoderReasoningEffortWithoutChangingOtherAgentDefaults() {
        AtomicReference<Map<String, Object>> options = new AtomicReference<>();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String providerId() {
                return "test";
            }

            @Override
            public ChatResponse chat(ChatRequest request) {
                options.set(request.options());
                return new ChatResponse("test", "model", "print('ok')", "stop");
            }
        };
        AgentContext context = new AgentContext("task-1", TenantId.of("tenant-a"),
                Map.of("plan", "print a result", "language", "python"), provider, null,
                Map.of("coderReasoningEffort", "medium"));

        AgentResult result = new CoderAgent().execute(context);

        assertEquals(AgentResult.Status.COMPLETED, result.status());
        assertEquals("medium", options.get().get("reasoning_effort"));
    }

    @Test
    void retriesCoderWithFallbackBudgetAfterGatewayTimeout() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<ChatRequest> first = new AtomicReference<>();
        AtomicReference<ChatRequest> second = new AtomicReference<>();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String providerId() {
                return "test";
            }

            @Override
            public ChatResponse chat(ChatRequest request) {
                if (calls.getAndIncrement() == 0) {
                    first.set(request);
                    throw new LlmException("LLM_PROVIDER_HTTP_524", "gateway timed out");
                }
                second.set(request);
                return new ChatResponse("test", "model", "print('fallback')", "stop");
            }
        };
        AgentContext context = new AgentContext("task-1", TenantId.of("tenant-a"),
                Map.of("plan", "print a result", "language", "python"), provider, null,
                Map.of("coderReasoningEffort", "medium", "coderFallbackReasoningEffort", "low",
                        "coderFallbackEnabled", true, "coderMaxOutputTokens", 1024,
                        "coderTimeout", java.time.Duration.ofSeconds(5)));

        AgentResult result = new CoderAgent().execute(context);

        assertEquals(AgentResult.Status.COMPLETED, result.status());
        assertEquals(2, calls.get());
        assertEquals("medium", first.get().options().get("reasoning_effort"));
        assertEquals("low", second.get().options().get("reasoning_effort"));
        assertEquals(1024, first.get().budget().maxOutputTokens());
        assertEquals(java.time.Duration.ofSeconds(5), first.get().budget().timeout());
    }

    @Test
    void stopsPrerequisiteFailureBeforeRuntime() {
        LlmProvider provider = new LlmProvider() {
            @Override
            public String providerId() {
                return "test";
            }

            @Override
            public ChatResponse chat(ChatRequest request) {
                return new ChatResponse("test", "model",
                        "print({'status': 'prerequisite_failure', 'missing_prerequisites': ['dataset']})",
                        "stop");
            }
        };
        AgentContext context = new AgentContext("task-1", TenantId.of("tenant-a"),
                Map.of("plan", "run a scientific experiment", "language", "python"), provider, null);

        AgentResult result = new CoderAgent().execute(context);

        assertEquals(AgentResult.Status.FAILED, result.status());
        assertEquals("PYTHON_INPUT_MISSING", result.outputs().get("toolErrorCode"));
        assertTrue(Boolean.TRUE.equals(result.outputs().get("prerequisiteFailure")));
        assertTrue(result.message().startsWith("PYTHON_INPUT_MISSING:"));
    }

    @Test
    void recognizesExplicitPrerequisiteFailureImplementation() {
        LlmProvider provider = new LlmProvider() {
            @Override
            public String providerId() {
                return "test";
            }

            @Override
            public ChatResponse chat(ChatRequest request) {
                return new ChatResponse("test", "model", "class PrerequisiteFailure(RuntimeError): pass\n"
                        + "raise PrerequisiteFailure('前置条件失败：缺少 OCR 文字稿')", "stop");
            }
        };
        AgentContext context = new AgentContext("task-1", TenantId.of("tenant-a"),
                Map.of("plan", "transcribe the attached WBS images", "language", "python"), provider, null);

        AgentResult result = new CoderAgent().execute(context);

        assertEquals(AgentResult.Status.FAILED, result.status());
        assertEquals("PYTHON_INPUT_MISSING", result.outputs().get("toolErrorCode"));
    }

    @Test
    void doesNotRejectExecutableInputGuardMessageAsMissingInput() {
        LlmProvider provider = new LlmProvider() {
            @Override
            public String providerId() {
                return "test";
            }

            @Override
            public ChatResponse chat(ChatRequest request) {
                return new ChatResponse("test", "model",
                        "from pathlib import Path\n"
                                + "asset = Path('/workspace/input/document-assets/image.png')\n"
                                + "if not asset.is_file():\n"
                                + "    raise RuntimeError('前置条件失败：输入图片不存在')\n"
                                + "print('ok')", "stop");
            }
        };
        AgentContext context = new AgentContext("task-1", TenantId.of("tenant-a"),
                Map.of("plan", "read the supplied image", "language", "python"), provider, null);

        AgentResult result = new CoderAgent().execute(context);

        assertEquals(AgentResult.Status.COMPLETED, result.status());
        assertFalse(result.outputs().containsKey("toolErrorCode"));
    }

    @Test
    void givesCoderFixedSourceDocumentContractInsteadOfOriginalUploadPath() {
        AtomicReference<ChatRequest> request = new AtomicReference<>();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String providerId() {
                return "test";
            }

            @Override
            public ChatResponse chat(ChatRequest value) {
                request.set(value);
                return new ChatResponse("test", "model", "print('ok')", "stop");
            }
        };
        AgentContext context = new AgentContext("task-1", TenantId.of("tenant-a"), Map.of(
                "plan", "read the supplied report only when necessary",
                "language", "python",
                "sourceDocumentInput", Map.of(
                        "relativePath", "source-document.docx",
                        "originalFileName", "original-upload.docx",
                        "mediaType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "size", 123L,
                        "sha256", "a".repeat(64))), provider, null);

        AgentResult result = new CoderAgent().execute(context);

        assertEquals(AgentResult.Status.COMPLETED, result.status());
        String system = request.get().messages().get(0).content();
        String user = request.get().messages().get(1).content();
        org.junit.jupiter.api.Assertions.assertTrue(system.contains("/workspace/input/source-document.docx"));
        org.junit.jupiter.api.Assertions.assertTrue(system.contains("Never use the original"));
        org.junit.jupiter.api.Assertions.assertTrue(user.contains("source-document.docx"));
        org.junit.jupiter.api.Assertions.assertTrue(user.contains("original-upload.docx"));
        org.junit.jupiter.api.Assertions.assertTrue(user.contains("/workspace/input/source-document.docx"));
    }

    @Test
    void normalizesAbsoluteHostDocxPathToTheMountedSourceDocument() {
        LlmProvider provider = new LlmProvider() {
            @Override
            public String providerId() {
                return "test";
            }

            @Override
            public ChatResponse chat(ChatRequest request) {
                return new ChatResponse("test", "model",
                        "from pathlib import Path\n"
                                + "print(Path(r\"C:\\\\Users\\\\NJHTR\\\\Desktop\\\\upload.docx\"))",
                        "stop");
            }
        };
        AgentContext context = new AgentContext("task-1", TenantId.of("tenant-a"), Map.of(
                "plan", "inspect the supplied DOCX",
                "language", "python",
                "sourceDocumentInput", Map.of("relativePath", "source-document.docx",
                        "size", 42L, "sha256", "c".repeat(64))), provider, null);

        AgentResult result = new CoderAgent().execute(context);

        assertEquals(AgentResult.Status.COMPLETED, result.status());
        assertEquals(Boolean.TRUE, result.outputs().get("sourceDocumentPathNormalized"));
        String source = String.valueOf(result.outputs().get("code"));
        assertTrue(source.contains("/workspace/input/source-document.docx"));
        assertFalse(source.contains("C:\\\\Users"));
    }

    @Test
    void includesBoundedRuntimeFailureFeedbackForSourceRepair() {
        AtomicReference<ChatRequest> request = new AtomicReference<>();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String providerId() { return "test"; }

            @Override
            public ChatResponse chat(ChatRequest value) {
                request.set(value);
                return new ChatResponse("test", "model", "print('fixed')", "stop");
            }
        };
        AgentContext context = new AgentContext("task-1", TenantId.of("tenant-a"), Map.of(
                "plan", "repair the DOCX report transformation",
                "language", "python",
                "runtimeFailureFeedback", Map.of("toolErrorCode", "PYTHON_RUNTIME_ERROR",
                        "stderr", "ValueError: invalid XML parent")), provider, null);

        AgentResult result = new CoderAgent().execute(context);

        assertEquals(AgentResult.Status.COMPLETED, result.status());
        String system = request.get().messages().get(0).content();
        String user = request.get().messages().get(1).content();
        org.junit.jupiter.api.Assertions.assertTrue(system.contains("actual"));
        org.junit.jupiter.api.Assertions.assertTrue(system.contains("parent first"));
        org.junit.jupiter.api.Assertions.assertTrue(user.contains("previousRuntimeFailure"));
        org.junit.jupiter.api.Assertions.assertTrue(user.contains("invalid XML parent"));
    }

    @Test
    void rejectsProviderResponseTruncatedByCompletionLimit() {
        LlmProvider provider = new LlmProvider() {
            @Override
            public String providerId() { return "test"; }

            @Override
            public ChatResponse chat(ChatRequest request) {
                return new ChatResponse("test", "model", "def incomplete():", "length");
            }
        };
        AgentContext context = new AgentContext("task-1", TenantId.of("tenant-a"),
                Map.of("plan", "generate a complete report program", "language", "python"), provider, null);

        AgentResult result = new CoderAgent().execute(context);

        assertEquals(AgentResult.Status.FAILED, result.status());
        assertEquals("LLM_OUTPUT_TRUNCATED", result.outputs().get("toolErrorCode"));
        assertTrue(result.outputs().containsKey("runtimeFailureFeedback"));
    }
}
