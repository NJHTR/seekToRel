package com.reportai.workflow;

import com.reportai.common.id.UuidIdGenerator;
import com.reportai.workflow.memory.InMemoryWorkflowDefinitionRepository;
import com.reportai.workflow.memory.InMemoryWorkflowEventRepository;
import com.reportai.workflow.memory.InMemoryWorkflowInstanceRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowEventTest {
    @Test
    void emitsEventsForStateTransitions() {
        InMemoryWorkflowDefinitionRepository definitions = new InMemoryWorkflowDefinitionRepository();
        definitions.save(new WorkflowDefinition("report", 1, "start", Map.of(
                "start", new WorkflowNode("start", "Start", WorkflowNodeType.START)), Set.of()));
        InMemoryWorkflowEventRepository events = new InMemoryWorkflowEventRepository();
        DefaultWorkflowEngine engine = new DefaultWorkflowEngine(definitions,
                new InMemoryWorkflowInstanceRepository(), new UuidIdGenerator(),
                new WorkflowStateMachine(), events);
        WorkflowInstance running = engine.start("report", "task-1");
        WorkflowInstance paused = engine.pause(running.id(), running.version());
        assertEquals(2, events.list(running.id(), -1, 10).size());
        assertEquals(WorkflowAction.START, events.list(running.id(), -1, 10).get(0).action());

        WorkflowInstance checkpointed = engine.checkpoint(paused.id(), paused.version(), "review",
                Map.of("agentIndex", 1, "answer", "ok"));
        assertEquals(WorkflowStatus.PAUSED, checkpointed.status());
        assertEquals("review", checkpointed.currentNodeId());
        assertEquals("ok", checkpointed.state().get("answer"));
        assertEquals(WorkflowAction.CHECKPOINT, events.list(running.id(), paused.version(), 10).get(0).action());
        assertTrue(checkpointed.version() > paused.version());
    }
}
