package com.reportai.api.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** Validated metadata for a multipart document upload. Binary content stays in the request stream. */
public record UploadDocumentRequest(
        @NotBlank String fileName,
        @NotBlank String mediaType,
        @Positive long contentLength) {
}
