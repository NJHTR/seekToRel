package com.reportai.application.progress;

import com.reportai.application.task.TaskHttpApplicationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Authenticated live progress endpoint. Task lookup enforces tenant isolation before subscribing. */
@RestController
@RequestMapping("/api/v1/tasks")
public final class ProgressEventController {
    private final TaskHttpApplicationService tasks;
    private final ProgressEventHub hub;

    public ProgressEventController(TaskHttpApplicationService tasks, ProgressEventHub hub) {
        this.tasks = tasks;
        this.hub = hub;
    }

    @GetMapping(value = "/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String taskId) {
        tasks.get(taskId);
        return hub.subscribe(taskId);
    }
}
