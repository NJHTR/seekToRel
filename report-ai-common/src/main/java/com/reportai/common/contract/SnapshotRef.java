package com.reportai.common.contract;

import java.util.Objects;

/** Immutable reference to content kept outside the contract payload. */
public record SnapshotRef(String name, String uri, String mediaType, long size, Sha256Digest digest) {
    public SnapshotRef {
        name = boundedText(name, "name", 128);
        uri = boundedText(uri, "uri", 1024);
        mediaType = boundedText(mediaType, "mediaType", 128);
        if (size < 0) {
            throw new IllegalArgumentException("snapshot reference size must not be negative");
        }
        digest = Objects.requireNonNull(digest, "digest");
    }

    private static String boundedText(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (!normalized.equals(value) || normalized.isBlank() || normalized.length() > max
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }
}
