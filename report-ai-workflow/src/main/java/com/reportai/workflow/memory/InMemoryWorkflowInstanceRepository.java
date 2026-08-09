package com.reportai.workflow.memory;

import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;
import com.reportai.workflow.WorkflowInstance;
import com.reportai.workflow.WorkflowInstanceRepository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** CAS semantics model the SQL UPDATE ... WHERE version = expectedVersion contract. */
public final class InMemoryWorkflowInstanceRepository implements WorkflowInstanceRepository {
    private final ConcurrentMap<String, WorkflowInstance> instances = new ConcurrentHashMap<>();

    @Override
    public Optional<WorkflowInstance> findById(String workflowId) {
        return Optional.ofNullable(instances.get(workflowId));
    }

    @Override
    public Optional<WorkflowInstance> findByTaskId(String taskId) {
        return instances.values().stream().filter(instance -> taskId.equals(instance.taskId())).findFirst();
    }

    @Override
    public WorkflowInstance create(WorkflowInstance instance) {
        if (instances.putIfAbsent(instance.id(), instance) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "workflow already exists");
        }
        return instance;
    }

    @Override
    public WorkflowInstance update(WorkflowInstance instance, long expectedVersion) {
        final WorkflowInstance[] previous = new WorkflowInstance[1];
        instances.compute(instance.id(), (id, current) -> {
            previous[0] = current;
            if (current == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "workflow not found");
            }
            if (current.version() != expectedVersion) {
                throw new BusinessException(ErrorCode.CONFLICT, "workflow version conflict");
            }
            if (instance.version() != expectedVersion + 1) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "workflow version must increment by one");
            }
            return instance;
        });
        return instance;
    }
}
