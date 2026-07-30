package com.reportai.agent;

import com.reportai.common.tenant.TenantId;
import com.reportai.llm.LlmProvider;
import com.reportai.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentPipelineExecutorTest {
    @Test
    void commitsOneAgentAtATimeAndResumesFromDurableCursor() {
        InMemoryAgentPipelineRepository repository = new InMemoryAgentPipelineRepository();
        Agent first = agent("first", AgentResult.Status.COMPLETED, Map.of("firstOutput", "ok"));
        Agent second = new Agent() {
            private int calls;

            @Override
            public String type() {
                return "second";
            }

            @Override
            public AgentResult execute(AgentContext context) {
                calls++;
                return new AgentResult(calls == 1 ? AgentResult.Status.WAITING : AgentResult.Status.COMPLETED,
                        Map.of("secondCalls", calls), calls == 1 ? "waiting for runtime" : "done");
            }
        };
        AgentPipelineExecutor executor = new AgentPipelineExecutor(new DefaultAgentOrchestrator(), repository);
        AgentContext context = new AgentContext("task-1", new TenantId("tenant-1"), Map.of("input", "x"),
                (LlmProvider) null, (ToolRegistry) null);

        AgentPipelineResult firstStep = executor.run("pipeline-1", context, List.of(first, second));
        assertEquals(AgentPipelineStatus.RUNNING, firstStep.status());
        assertEquals(1, firstStep.checkpoint().nextAgentIndex());
        assertEquals(List.of("first"), firstStep.checkpoint().state().get("completedAgents"));

        AgentPipelineResult waiting = executor.run("pipeline-1", context, List.of(first, second));
        assertEquals(AgentPipelineStatus.WAITING, waiting.status());
        assertEquals(1, waiting.checkpoint().nextAgentIndex());
        assertEquals("second", waiting.checkpoint().state().get("waitingAgent"));

        AgentPipelineResult completed = executor.run("pipeline-1", context, List.of(first, second));
        assertEquals(AgentPipelineStatus.COMPLETED, completed.status());
        assertEquals(2, completed.checkpoint().nextAgentIndex());
        assertEquals(3, completed.checkpoint().version());
        assertEquals(List.of("first", "second"), completed.checkpoint().state().get("completedAgents"));
        assertEquals("x", completed.checkpoint().state().get("input"));
        assertEquals("ok", completed.checkpoint().state().get("firstOutput"));
    }

    @Test
    void rejectsRuntimeAgentListThatCannotSafelyResumeCheckpoint() {
        AgentPipelineExecutor executor = new AgentPipelineExecutor(new DefaultAgentOrchestrator(),
                new InMemoryAgentPipelineRepository());
        AgentContext context = new AgentContext("task-1", new TenantId("tenant-1"), Map.of(),
                (LlmProvider) null, (ToolRegistry) null);
        executor.start("pipeline-1", context, List.of(agent("first", AgentResult.Status.COMPLETED, Map.of())));
        assertThrows(IllegalArgumentException.class,
                () -> executor.run("pipeline-1", context, List.of(agent("different", AgentResult.Status.COMPLETED,
                        Map.of()))));
    }

    private static Agent agent(String type, AgentResult.Status status, Map<String, Object> outputs) {
        return new Agent() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public AgentResult execute(AgentContext context) {
                return new AgentResult(status, outputs, "result");
            }
        };
    }
}
