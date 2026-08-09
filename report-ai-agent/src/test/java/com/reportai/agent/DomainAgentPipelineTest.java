package com.reportai.agent;

import com.reportai.common.tenant.TenantId;
import com.reportai.document.model.BlockType;
import com.reportai.document.model.Document;
import com.reportai.document.model.DocumentBlock;
import com.reportai.llm.ChatMessage;
import com.reportai.llm.ChatRequest;
import com.reportai.llm.ChatResponse;
import com.reportai.llm.LlmProvider;
import com.reportai.review.CompletenessReviewEngine;
import com.reportai.tool.Tool;
import com.reportai.tool.ToolContext;
import com.reportai.tool.ToolInput;
import com.reportai.tool.ToolRegistry;
import com.reportai.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainAgentPipelineTest {
    @Test
    void executesBoundedDomainPipelineThroughLlmAndToolPorts() {
        TenantId tenant = new TenantId("tenant-1");
        Document document = new Document("doc-1", tenant, "reports/source.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                List.of(new DocumentBlock(BlockType.TITLE, "Sorting experiment", List.of(), null),
                        new DocumentBlock(BlockType.PARAGRAPH, "Experiment result", List.of(), null)),
                List.of(), List.of());
        AgentContext context = AgentPrecheckFixtures.context("task-1", tenant, "pipeline",
                Map.of("document", document, "language", "python",
                        "runtimeRequest", Map.of("entryPoint", "main.py")), new FakeLlm(), new FakeTools());

        AgentResult result = new DefaultAgentOrchestrator(new AgentExecutionPolicy(8, 128, 256), AgentPrecheckFixtures.guard())
                .execute(context, List.of(new PlannerAgent(), new CoderAgent(), new RuntimeAgent(),
                        new DocumentAgent(), new ReviewerAgent(new CompletenessReviewEngine())));

        assertEquals(AgentResult.Status.COMPLETED, result.status());
        assertTrue(result.outputs().containsKey("plan"));
        assertEquals("print('ok')", result.outputs().get("code"));
        assertEquals(Map.of("status", "SUCCEEDED"), result.outputs().get("execution"));
        assertEquals("draft", result.outputs().get("documentDraft"));
        assertEquals(Boolean.TRUE, result.outputs().get("reviewPassed"));
        assertEquals(List.of("planner", "coder", "runtime", "document", "reviewer"),
                result.outputs().get("completedAgents"));
    }

    @Test
    void refusesPipelineThatExceedsStepBudget() {
        AgentContext context = new AgentContext("task-1", new TenantId("tenant-1"),
                Map.of("document", "input"), new FakeLlm(), new FakeTools());
        Agent noop = new Agent() {
            @Override
            public String type() {
                return "noop";
            }

            @Override
            public AgentResult execute(AgentContext ignored) {
                return new AgentResult(AgentResult.Status.COMPLETED, Map.of(), "ok");
            }
        };
        AgentResult result = new DefaultAgentOrchestrator(new AgentExecutionPolicy(2, 16, 64))
                .execute(context, List.of(noop, noop, noop));
        assertEquals(AgentResult.Status.FAILED, result.status());
        assertTrue(result.message().contains("step limit"));
    }

    private static final class FakeLlm implements LlmProvider {
        @Override
        public String providerId() {
            return "test";
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            String prompt = request.messages().get(0).content();
            String content = prompt.contains("Planner Agent") ? "step 1: run python"
                    : prompt.contains("Coder Agent") ? "```python\nprint('ok')\n```"
                    : prompt.contains("Document Agent") ? "draft" : "answer";
            return new ChatResponse("test", "test-model", content, "stop");
        }
    }

    private static final class FakeTools implements ToolRegistry {
        private final Tool python = new Tool() {
            @Override
            public String name() {
                return "python.run";
            }

            @Override
            public ToolResult execute(ToolContext context, ToolInput input) {
                return new ToolResult(ToolResult.Status.SUCCEEDED, Map.of("status", "SUCCEEDED"), "ok");
            }
        };

        @Override
        public void register(Tool tool) {
        }

        @Override
        public Optional<Tool> find(String name) {
            return "python.run".equals(name) ? Optional.of(python) : Optional.empty();
        }

        @Override
        public Set<String> names() {
            return Set.of("python.run");
        }
    }
}
