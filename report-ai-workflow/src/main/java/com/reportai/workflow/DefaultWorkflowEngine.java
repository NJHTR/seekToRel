package com.reportai.workflow;

import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.id.IdGenerator;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Application service coordinating a definition repository, state machine and CAS repository. */
public final class DefaultWorkflowEngine implements WorkflowEngine {
    private static final WorkflowEventRepository NO_OP_EVENTS = new WorkflowEventRepository() {
        @Override
        public void append(WorkflowEvent event) {
            // Intentionally empty for in-memory/default construction.
        }

        @Override
        public List<WorkflowEvent> list(String workflowId, long afterVersion, int limit) {
            return List.of();
        }
    };

    private final WorkflowDefinitionRepository definitions;
    private final WorkflowInstanceRepository instances;
    private final IdGenerator idGenerator;
    private final WorkflowStateMachine stateMachine;
    private final WorkflowEventRepository events;

    public DefaultWorkflowEngine(WorkflowDefinitionRepository definitions,
                                 WorkflowInstanceRepository instances,
                                 IdGenerator idGenerator) {
        this(definitions, instances, idGenerator, new WorkflowStateMachine(), NO_OP_EVENTS);
    }

    public DefaultWorkflowEngine(WorkflowDefinitionRepository definitions,
                                 WorkflowInstanceRepository instances,
                                 IdGenerator idGenerator,
                                 WorkflowStateMachine stateMachine) {
        this(definitions, instances, idGenerator, stateMachine, NO_OP_EVENTS);
    }

    public DefaultWorkflowEngine(WorkflowDefinitionRepository definitions,
                                 WorkflowInstanceRepository instances,
                                 IdGenerator idGenerator,
                                 WorkflowStateMachine stateMachine,
                                 WorkflowEventRepository events) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.instances = Objects.requireNonNull(instances, "instances");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine");
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override
    public WorkflowInstance start(String definitionId, String taskId) {
        if (definitionId == null || definitionId.isBlank() || taskId == null || taskId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "definitionId and taskId are required");
        }
        WorkflowDefinition definition = definitions.findLatest(definitionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "workflow definition not found"));
        WorkflowInstance created = new WorkflowInstance(idGenerator.nextId(), definition.id(), definition.version(),
                taskId, definition.startNodeId(), WorkflowStatus.CREATED, Map.of("taskId", taskId), 0);
        WorkflowInstance persisted = instances.create(created);
        return transition(persisted.id(), persisted.version(), WorkflowAction.START);
    }

    @Override
    public WorkflowInstance resume(String workflowId, long expectedVersion) {
        return transition(workflowId, expectedVersion, WorkflowAction.RESUME);
    }

    @Override
    public WorkflowInstance pause(String workflowId, long expectedVersion) {
        return transition(workflowId, expectedVersion, WorkflowAction.PAUSE);
    }

    @Override
    public WorkflowInstance cancel(String workflowId, long expectedVersion) {
        return transition(workflowId, expectedVersion, WorkflowAction.CANCEL);
    }

    @Override
    public WorkflowInstance complete(String workflowId, long expectedVersion) {
        return transition(workflowId, expectedVersion, WorkflowAction.COMPLETE);
    }

    @Override
    public WorkflowInstance fail(String workflowId, long expectedVersion) {
        return transition(workflowId, expectedVersion, WorkflowAction.FAIL);
    }

    @Override
    public WorkflowInstance checkpoint(String workflowId, long expectedVersion,
                                       String currentNodeId, Map<String, Object> state) {
        WorkflowInstance current = get(workflowId);
        if (expectedVersion < 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "expectedVersion must not be negative");
        }
        if (currentNodeId == null || currentNodeId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "currentNodeId is required");
        }
        WorkflowStatus nextStatus = stateMachine.transition(current.status(), WorkflowAction.CHECKPOINT);
        WorkflowInstance next = current.withCheckpoint(currentNodeId, state, current.version() + 1)
                .withTransition(nextStatus, current.version() + 1);
        WorkflowInstance updated = instances.update(next, expectedVersion);
        events.append(new WorkflowEvent(idGenerator.nextId(), updated.id(), WorkflowAction.CHECKPOINT,
                current.status(), updated.status(), updated.version(), java.time.Instant.now(),
                Map.of("definitionId", updated.definitionId(), "currentNodeId", updated.currentNodeId())));
        return updated;
    }

    @Override
    public WorkflowInstance get(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "workflowId is required");
        }
        return instances.findById(workflowId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "workflow not found"));
    }

    @Override
    public Optional<WorkflowInstance> findByTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "taskId is required");
        }
        return instances.findByTaskId(taskId);
    }

    private WorkflowInstance transition(String workflowId, long expectedVersion, WorkflowAction action) {
        WorkflowInstance current = get(workflowId);
        if (expectedVersion < 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "expectedVersion must not be negative");
        }
        WorkflowStatus nextStatus = stateMachine.transition(current.status(), action);
        WorkflowInstance next = current.withTransition(nextStatus, current.version() + 1);
        WorkflowInstance updated = instances.update(next, expectedVersion);
        events.append(new WorkflowEvent(idGenerator.nextId(), updated.id(), action, current.status(),
                updated.status(), updated.version(), java.time.Instant.now(), Map.of("definitionId", updated.definitionId())));
        return updated;
    }
}
