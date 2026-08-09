package com.reportai.application.task;

import com.reportai.common.api.Result;
import com.reportai.common.trace.TraceContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** HTTP boundary for tenant-owned task input files. */
@RestController
@RequestMapping("/api/v1/tasks")
public final class TaskInputController {
    private final TaskInputApplicationService inputs;

    public TaskInputController(TaskInputApplicationService inputs) {
        this.inputs = inputs;
    }

    @PostMapping(value = "/{taskId}/inputs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<TaskInputApplicationService.InputMetadata> upload(
            @PathVariable String taskId,
            @RequestParam("path") String path,
            @RequestPart("file") MultipartFile file) {
        return Result.success(inputs.upload(taskId, path, file), TraceContext.getOrCreate());
    }

    @GetMapping(value = "/{taskId}/inputs", produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<List<TaskInputApplicationService.InputMetadata>> list(@PathVariable String taskId) {
        return Result.success(inputs.list(taskId), TraceContext.getOrCreate());
    }
}
