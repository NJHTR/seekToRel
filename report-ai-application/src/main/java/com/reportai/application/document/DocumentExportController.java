package com.reportai.application.document;

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

@RestController
@RequestMapping("/api/v1/documents")
public final class DocumentExportController {
    private static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private final DocumentExportApplicationService service;

    public DocumentExportController(DocumentExportApplicationService service) {
        this.service = service;
    }

    @GetMapping(value = "/{documentId}/export", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> export(@PathVariable String documentId) {
        byte[] bytes = service.export(documentId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(DOCX);
        headers.setContentLength(bytes.length);
        headers.setContentDisposition(ContentDisposition.attachment().filename("report-" + documentId + ".docx").build());
        headers.set("X-Content-SHA256", sha256(bytes));
        headers.set("X-Trace-Id", TraceContext.getOrCreate());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
