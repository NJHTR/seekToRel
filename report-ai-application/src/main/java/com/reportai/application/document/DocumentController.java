package com.reportai.application.document;

import com.reportai.api.document.UploadDocumentResponse;
import com.reportai.common.api.Result;
import com.reportai.common.trace.TraceContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** HTTP adapter for streamed document ingestion. Business decisions stay in the application service. */
@RestController
@RequestMapping("/api/v1/documents")
public final class DocumentController {
    private final DocumentUploadApplicationService uploadService;

    public DocumentController(DocumentUploadApplicationService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<UploadDocumentResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.success(uploadService.upload(file, idempotencyKey), TraceContext.getOrCreate());
    }
}
