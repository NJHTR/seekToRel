package com.reportai.workflow.memory;

import com.reportai.workflow.WorkflowEvent;
import com.reportai.workflow.WorkflowEventRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryWorkflowEventRepository implements WorkflowEventRepository {
    private final CopyOnWriteArrayList<WorkflowEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void append(WorkflowEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event is required");
        }
        events.addIfAbsent(event);
    }

    @Override
    public List<WorkflowEvent> list(String workflowId, long afterVersion, int limit) {
        if (workflowId == null || workflowId.isBlank() || afterVersion < -1 || limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("invalid event query");
        }
        List<WorkflowEvent> result = new ArrayList<>();
        for (WorkflowEvent event : events) {
            if (workflowId.equals(event.workflowId()) && event.version() > afterVersion) {
                result.add(event);
                if (result.size() == limit) {
                    break;
                }
            }
        }
        return List.copyOf(result);
    }
}
