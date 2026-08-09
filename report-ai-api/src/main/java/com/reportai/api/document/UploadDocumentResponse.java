package com.reportai.api.document;

import java.util.List;

/** Stable response; object storage keys are intentionally not exposed to clients. */
public record UploadDocumentResponse(
        String documentId,
        String status,
        String mediaType,
        long contentLength,
        List<String> warnings) {
    public UploadDocumentResponse {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException("mediaType must not be blank");
        }
        if (contentLength <= 0) {
            throw new IllegalArgumentException("contentLength must be positive");
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
