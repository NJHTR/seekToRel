package com.reportai.agent;

import com.reportai.common.tenant.TenantId;
import com.reportai.llm.LlmProvider;
import com.reportai.llm.ChatContentPart;
import com.reportai.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultAgentOrchestratorTest {
    @Test
    void mergesOutputsAndStopsOnWaiting() {
        AgentContext context = new AgentContext("task-1", new TenantId("tenant-1"), Map.of("input", "x"),
                (LlmProvider) null, (ToolRegistry) null);
        Agent first = new Agent() {
            public String type() { return "first"; }
            public AgentResult execute(AgentContext ignored) {
                return new AgentResult(AgentResult.Status.COMPLETED, Map.of("a", 1), "ok");
            }
        };
        Agent second = new Agent() {
            public String type() { return "second"; }
            public AgentResult execute(AgentContext ignored) {
                return new AgentResult(AgentResult.Status.WAITING, Map.of("b", 2), "waiting");
            }
        };
        AgentResult result = new DefaultAgentOrchestrator().execute(context, List.of(first, second));
        assertEquals(AgentResult.Status.WAITING, result.status());
        assertEquals(1, result.outputs().get("a"));
        assertEquals(List.of("first", "second"), result.outputs().get("completedAgents"));
    }

    @Test
    void preservesEphemeralAttachmentResolverForEachAgentStep() {
        ChatContentPart image = ChatContentPart.image("image/png", new byte[]{1});
        AgentContext context = new AgentContext("task-vision", new TenantId("tenant-1"), Map.of(),
                null, null, Map.of(), (agent, state) -> List.of(image));
        Agent agent = new Agent() {
            @Override
            public String type() {
                return "planner";
            }

            @Override
            public AgentResult execute(AgentContext step) {
                assertEquals(List.of(image), step.attachmentResolver().resolve(type(), step.state()));
                return new AgentResult(AgentResult.Status.COMPLETED, Map.of("ok", true), "done");
            }
        };

        AgentResult result = new DefaultAgentOrchestrator().execute(context, List.of(agent));

        assertEquals(AgentResult.Status.COMPLETED, result.status());
        assertEquals(true, result.outputs().get("ok"));
    }
}
