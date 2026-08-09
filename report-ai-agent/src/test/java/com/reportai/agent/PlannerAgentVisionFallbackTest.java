package com.reportai.agent;

import com.reportai.common.tenant.TenantId;
import com.reportai.llm.ChatContentPart;
import com.reportai.llm.ChatRequest;
import com.reportai.llm.ChatResponse;
import com.reportai.llm.LlmProvider;
import com.reportai.llm.OcrPage;
import com.reportai.llm.OcrRequest;
import com.reportai.llm.OcrResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlannerAgentVisionFallbackTest {
    @Test
    void tellsPlannerTheCanonicalSourceAndFixedSandboxPath() {
        AtomicReference<ChatRequest> request = new AtomicReference<>();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String providerId() {
                return "test";
            }

            @Override
            public ChatResponse chat(ChatRequest value) {
                request.set(value);
                return new ChatResponse("test", "model", "plan", "stop");
            }
        };
        AgentContext context = new AgentContext("task-1", TenantId.of("tenant-a"), Map.of(
                "document", "canonical document",
                "sourceDocumentInput", Map.of("relativePath", "source-document.docx",
                        "originalFileName", "upload.docx", "size", 42L,
                        "sha256", "b".repeat(64))), provider, null);

        AgentResult result = new PlannerAgent().execute(context);

        assertEquals(AgentResult.Status.COMPLETED, result.status());
        String system = request.get().messages().get(0).content();
        String user = request.get().messages().get(1).content();
        org.junit.jupiter.api.Assertions.assertTrue(system.contains("/workspace/input/source-document.docx"));
        org.junit.jupiter.api.Assertions.assertTrue(user.contains("providedSourceDocumentInput"));
        org.junit.jupiter.api.Assertions.assertTrue(user.contains("Never use originalFileName"));
    }

    @Test
    void retriesWithProviderOcrWhenMultimodalModelCannotSeeImage() {
        AtomicInteger chats = new AtomicInteger();
        AtomicReference<ChatRequest> recovered = new AtomicReference<>();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String providerId() {
                return "test";
            }

            @Override
            public ChatResponse chat(ChatRequest request) {
                if (chats.getAndIncrement() == 0) {
                    return new ChatResponse("test", "model", "I cannot see the image attached to the request.", "stop");
                }
                recovered.set(request);
                return new ChatResponse("test", "model", "plan from OCR", "stop");
            }

            @Override
            public OcrResponse ocr(OcrRequest request) {
                return new OcrResponse("test", "model",
                        List.of(new OcrPage(1, "WBS: 软件项目范围管理", 0.0)), null);
            }
        };
        AgentContext context = new AgentContext("task-1", TenantId.of("tenant-a"),
                Map.of("document", "canonical document"), provider, null, Map.of(),
                (agent, state) -> List.of(ChatContentPart.image("image/png", new byte[]{1, 2, 3})));

        AgentResult result = new PlannerAgent().execute(context);

        assertEquals(AgentResult.Status.COMPLETED, result.status());
        assertEquals("plan from OCR", result.outputs().get("plan"));
        assertEquals(2, chats.get());
        assertFalse(recovered.get().messages().get(1).isMultimodal());
        org.junit.jupiter.api.Assertions.assertTrue(recovered.get().messages().get(1).content()
                .contains("WBS: 软件项目范围管理"));
    }
}
