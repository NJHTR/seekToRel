package com.reportai.application.task;

import com.reportai.api.report.CreateTaskRequest;
import com.reportai.api.report.TaskControlRequest;
import com.reportai.api.report.TaskResponse;
import com.reportai.common.api.Result;
import com.reportai.common.trace.TraceContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public final class TaskController {
    private final TaskHttpApplicationService service;

    public TaskController(TaskHttpApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public Result<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request,
                                       @RequestHeader(name = "Idempotency-Key", required = false)
                                       String idempotencyKey) {
        return Result.success(service.create(request, idempotencyKey), TraceContext.getOrCreate());
    }

    @GetMapping("/{taskId}")
    public Result<TaskResponse> get(@PathVariable String taskId) {
        return Result.success(service.get(taskId), TraceContext.getOrCreate());
    }

    @PostMapping("/{taskId}/status")
    public Result<TaskResponse> changeStatus(@PathVariable String taskId,
                                             @Valid @RequestBody TaskControlRequest request) {
        return Result.success(service.changeStatus(taskId, request), TraceContext.getOrCreate());
    }
}
