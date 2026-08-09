package com.reportai.parser;

import com.reportai.common.tenant.TenantId;

import java.io.InputStream;
import java.util.Objects;

/** Request boundary for parsers. The caller owns and closes the input stream. */
public record ParserRequest(InputStream input,
                            String fileName,
                            String sourceObjectKey,
                            TenantId tenantId,
                            String mediaType,
                            long contentLength,
                            String documentId) {
    /** Backward-compatible constructor for adapters that let the parser generate an id. */
    public ParserRequest(InputStream input,
                          String fileName,
                          String sourceObjectKey,
                          TenantId tenantId,
                          String mediaType,
                          long contentLength) {
        this(input, fileName, sourceObjectKey, tenantId, mediaType, contentLength, null);
    }

    public ParserRequest {
        input = Objects.requireNonNull(input, "input");
        fileName = requireText(fileName, "fileName", 255);
        sourceObjectKey = requireText(sourceObjectKey, "sourceObjectKey", 1024);
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        mediaType = requireText(mediaType, "mediaType", 128);
        if (contentLength < -1) {
            throw new IllegalArgumentException("contentLength must be -1 or non-negative");
        }
        if (documentId != null) {
            documentId = requireText(documentId, "documentId", 128);
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength || normalized.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }
}
