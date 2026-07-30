package com.reportai.document.security;

import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;

import java.util.Locale;
import java.util.Set;

/** Boundary checks; the concrete adapter must also virus-scan and defend against zip bombs. */
public record UploadPolicy(long maxBytes, Set<String> allowedMediaTypes, Set<String> allowedExtensions) {
    public UploadPolicy {
        if (maxBytes <= 0 || allowedMediaTypes == null || allowedMediaTypes.isEmpty()
                || allowedExtensions == null || allowedExtensions.isEmpty()) {
            throw new IllegalArgumentException("invalid upload policy");
        }
        allowedMediaTypes = Set.copyOf(allowedMediaTypes);
        allowedExtensions = Set.copyOf(allowedExtensions);
    }

    public static UploadPolicy docxDefaults() {
        return new UploadPolicy(20 * 1024 * 1024L,
                Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                Set.of(".docx"));
    }

    public void validate(String fileName, String mediaType, long contentLength, byte[] firstBytes) {
        if (fileName == null || fileName.isBlank() || fileName.length() > 255
                || fileName.contains("..") || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0) {
            throw invalid("invalid file name");
        }
        if (contentLength <= 0 || contentLength > maxBytes) {
            throw invalid("file size is outside the allowed range");
        }
        String normalizedType = mediaType == null ? "" : mediaType.toLowerCase(Locale.ROOT);
        if (!allowedMediaTypes.contains(normalizedType)) {
            throw invalid("unsupported media type");
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        boolean extensionAllowed = allowedExtensions.stream().anyMatch(lowerName::endsWith);
        if (!extensionAllowed || firstBytes == null || firstBytes.length < 4
                || firstBytes[0] != 'P' || firstBytes[1] != 'K'
                || firstBytes[2] != 3 || firstBytes[3] != 4) {
            throw invalid("file signature does not match a DOCX archive");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }
}
