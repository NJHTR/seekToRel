package com.reportai.application.task;

import com.reportai.api.report.CreateTaskRequest;
import com.reportai.api.report.TaskControlRequest;
import com.reportai.api.report.TaskResponse;
import com.reportai.api.report.TaskHistoryResponse;
import com.reportai.common.api.Result;
import com.reportai.common.trace.TraceContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

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

    /** Atomic variant for experiments that need one input file at task start. */
    @PostMapping(value = "/with-input", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<TaskResponse> createWithInput(
            @RequestParam(name = "documentId") String documentId,
            @RequestParam(name = "path") String path,
            @RequestPart("file") MultipartFile file,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.success(service.createWithInput(new CreateTaskRequest(documentId, null), idempotencyKey,
                path, file), TraceContext.getOrCreate());
    }

    @GetMapping
    public ResponseEntity<Result<TaskHistoryResponse>> listRecent(
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "50") String limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Result.success(service.listRecent(cursor, limit), TraceContext.getOrCreate()));
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
