package com.reportai.application.workflow;

import com.reportai.api.workflow.WorkflowControlRequest;
import com.reportai.api.workflow.WorkflowResponse;
import com.reportai.api.workflow.WorkflowStartRequest;
import com.reportai.api.workflow.WorkflowRoutes;
import com.reportai.common.api.Result;
import com.reportai.common.trace.TraceContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(WorkflowRoutes.WORKFLOWS)
public final class WorkflowController {
    private final WorkflowHttpApplicationService service;

    public WorkflowController(WorkflowHttpApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public Result<WorkflowResponse> start(@Valid @RequestBody WorkflowStartRequest request) {
        return Result.success(service.start(request), TraceContext.getOrCreate());
    }

    @GetMapping("/{workflowId}")
    public Result<WorkflowResponse> get(@PathVariable String workflowId) {
        return Result.success(service.get(workflowId), TraceContext.getOrCreate());
    }

    @PostMapping("/{workflowId}/pause")
    public Result<WorkflowResponse> pause(@PathVariable String workflowId,
                                          @Valid @RequestBody WorkflowControlRequest request) {
        return Result.success(service.pause(workflowId, request), TraceContext.getOrCreate());
    }

    @PostMapping("/{workflowId}/resume")
    public Result<WorkflowResponse> resume(@PathVariable String workflowId,
                                           @Valid @RequestBody WorkflowControlRequest request) {
        return Result.success(service.resume(workflowId, request), TraceContext.getOrCreate());
    }

    @PostMapping("/{workflowId}/cancel")
    public Result<WorkflowResponse> cancel(@PathVariable String workflowId,
                                           @Valid @RequestBody WorkflowControlRequest request) {
        return Result.success(service.cancel(workflowId, request), TraceContext.getOrCreate());
    }
}
