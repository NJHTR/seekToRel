package com.reportai.workflow;

import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/** Explicit transition table; invalid transitions fail closed. */
public final class WorkflowStateMachine {
    private final Map<WorkflowStatus, Map<WorkflowAction, WorkflowStatus>> transitions;

    public WorkflowStateMachine() {
        EnumMap<WorkflowStatus, Map<WorkflowAction, WorkflowStatus>> table = new EnumMap<>(WorkflowStatus.class);
        table.put(WorkflowStatus.CREATED, Map.of(
                WorkflowAction.START, WorkflowStatus.RUNNING,
                WorkflowAction.CANCEL, WorkflowStatus.CANCELLED));
        table.put(WorkflowStatus.RUNNING, Map.of(
                WorkflowAction.CHECKPOINT, WorkflowStatus.RUNNING,
                WorkflowAction.PAUSE, WorkflowStatus.PAUSED,
                WorkflowAction.COMPLETE, WorkflowStatus.SUCCEEDED,
                WorkflowAction.FAIL, WorkflowStatus.FAILED,
                WorkflowAction.CANCEL, WorkflowStatus.CANCELLED));
        table.put(WorkflowStatus.PAUSED, Map.of(
                WorkflowAction.CHECKPOINT, WorkflowStatus.PAUSED,
                WorkflowAction.RESUME, WorkflowStatus.RUNNING,
                WorkflowAction.CANCEL, WorkflowStatus.CANCELLED));
        table.put(WorkflowStatus.SUCCEEDED, Map.of());
        table.put(WorkflowStatus.FAILED, Map.of());
        table.put(WorkflowStatus.CANCELLED, Map.of());
        transitions = Map.copyOf(table);
    }

    public WorkflowStatus transition(WorkflowStatus current, WorkflowAction action) {
        if (current == null || action == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "workflow status and action are required");
        }
        WorkflowStatus next = transitions.getOrDefault(current, Map.of()).get(action);
        if (next == null) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "workflow cannot apply action " + action + " in status " + current);
        }
        return next;
    }

    public boolean canTransition(WorkflowStatus current, WorkflowAction action) {
        return current != null && action != null
                && transitions.getOrDefault(current, Map.of()).containsKey(action);
    }
}
