package com.reportai.workflow;

import com.reportai.common.id.UuidIdGenerator;
import com.reportai.workflow.memory.InMemoryWorkflowDefinitionRepository;
import com.reportai.workflow.memory.InMemoryWorkflowInstanceRepository;

import java.util.Map;
import java.util.Set;

/** Lightweight executable self-check usable without a test framework. */
public final class WorkflowEngineSmokeCheck {
    private WorkflowEngineSmokeCheck() {
    }

    public static void main(String[] args) {
        InMemoryWorkflowDefinitionRepository definitions = new InMemoryWorkflowDefinitionRepository();
        definitions.save(new WorkflowDefinition("report", 1, "start", Map.of(
                "start", new WorkflowNode("start", "Start", WorkflowNodeType.START),
                "review", new WorkflowNode("review", "Review", WorkflowNodeType.REVIEW)),
                Set.of(new WorkflowTransition("start", "review", "NEXT"))));
        DefaultWorkflowEngine engine = new DefaultWorkflowEngine(definitions,
                new InMemoryWorkflowInstanceRepository(), new UuidIdGenerator());

        WorkflowInstance running = engine.start("report", "task-1");
        require(running.status() == WorkflowStatus.RUNNING, "start transition");
        WorkflowInstance paused = engine.pause(running.id(), running.version());
        require(paused.status() == WorkflowStatus.PAUSED, "pause transition");
        WorkflowInstance resumed = engine.resume(paused.id(), paused.version());
        require(resumed.status() == WorkflowStatus.RUNNING, "resume transition");
        WorkflowInstance completed = engine.complete(resumed.id(), resumed.version());
        require(completed.status() == WorkflowStatus.SUCCEEDED, "complete transition");
        require(completed.version() == 4, "optimistic version increment");

        boolean rejected = false;
        try {
            engine.cancel(completed.id(), completed.version());
        } catch (RuntimeException expected) {
            rejected = true;
        }
        require(rejected, "terminal transition rejection");
        System.out.println("WORKFLOW_SMOKE_OK");
    }

    private static void require(boolean condition, String name) {
        if (!condition) {
            throw new IllegalStateException("failed: " + name);
        }
    }
}
