package com.reportai.storage;

import com.reportai.storage.objectstore.MediaTypeValidator;
import com.reportai.storage.objectstore.ObjectKeyValidator;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable metadata reference for a binary object stored outside PostgreSQL. */
public record StoredObject(String objectKey, String mediaType, long size, String sha256) {
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    public StoredObject {
        objectKey = ObjectKeyValidator.validate(objectKey);
        mediaType = MediaTypeValidator.validate(mediaType).toLowerCase(Locale.ROOT);
        if (mediaType.startsWith("text/")) {
            throw new IllegalArgumentException("text media types are not valid binary object references");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("object size must be positive");
        }
        sha256 = Objects.requireNonNull(sha256, "sha256").strip().toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("object sha256 must be a lowercase 64 character hex digest");
        }
    }
}
