package com.reportai.application.progress;

import com.reportai.application.task.TaskHttpApplicationService;
import com.reportai.api.report.ProgressEventHistoryResponse;
import com.reportai.common.api.Result;
import com.reportai.common.trace.TraceContext;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Authenticated live progress endpoint. Task lookup enforces tenant isolation before subscribing. */
@RestController
@RequestMapping("/api/v1/tasks")
public final class ProgressEventController {
    private final TaskHttpApplicationService tasks;
    private final ProgressEventHub hub;
    private final ProgressEventHistoryApplicationService history;

    public ProgressEventController(TaskHttpApplicationService tasks, ProgressEventHub hub,
                                   ProgressEventHistoryApplicationService history) {
        this.tasks = tasks;
        this.hub = hub;
        this.history = history;
    }

    @GetMapping(value = "/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String taskId,
                             @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        var tenantId = tasks.requireTenantForRead(taskId);
        return hub.subscribe(tenantId, taskId, parseLastEventId(lastEventId));
    }

    @GetMapping("/{taskId}/events/history")
    public ResponseEntity<Result<ProgressEventHistoryResponse>> history(
            @PathVariable String taskId,
            @RequestParam(name = "after", required = false) String after,
            @RequestParam(name = "limit", defaultValue = "50") String limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Result.success(history.list(taskId, after, limit), TraceContext.getOrCreate()));
    }

    private static Long parseLastEventId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (!raw.matches("[0-9]{1,19}")) {
            throw new IllegalArgumentException("Last-Event-ID is invalid");
        }
        try {
            long value = Long.parseLong(raw);
            return value < 1 ? null : value;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Last-Event-ID is invalid", failure);
        }
    }
}
