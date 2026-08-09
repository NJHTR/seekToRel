package com.reportai.application.task;

import com.reportai.common.trace.TraceContext;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/** Lists and downloads task-owned artifacts without exposing storage paths. */
@RestController
@RequestMapping("/api/v1/tasks")
public final class TaskArtifactController {
    private final TaskArtifactApplicationService service;

    public TaskArtifactController(TaskArtifactApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{taskId}/artifacts")
    public List<TaskArtifactApplicationService.ArtifactMetadata> list(@PathVariable String taskId) {
        return service.list(taskId);
    }

    @GetMapping("/{taskId}/artifacts/{artifactId}")
    public ResponseEntity<byte[]> download(@PathVariable String taskId, @PathVariable String artifactId) {
        TaskArtifactApplicationService.Download download = service.download(taskId, artifactId);
        var reference = download.reference();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(reference.mediaType()));
        headers.setContentLength(reference.size());
        headers.setContentDisposition(ContentDisposition.attachment().filename(artifactId + ".bin").build());
        headers.set("X-Content-SHA256", sha256(download.bytes()));
        headers.set("X-Trace-Id", TraceContext.getOrCreate());
        return ResponseEntity.ok().headers(headers).body(download.bytes());
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
