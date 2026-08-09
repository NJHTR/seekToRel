package com.reportai.api.report;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
        String documentId,
        @Size(max = 512) @Pattern(regexp = "[A-Za-z0-9/_:.\\-]{1,512}")
        String documentObjectKey) {

    public CreateTaskRequest(String documentObjectKey) {
        this(null, documentObjectKey);
    }

    @AssertTrue(message = "documentId or documentObjectKey is required")
    public boolean hasDocumentReference() {
        return (documentId != null && !documentId.isBlank())
                || (documentObjectKey != null && !documentObjectKey.isBlank());
    }

}
