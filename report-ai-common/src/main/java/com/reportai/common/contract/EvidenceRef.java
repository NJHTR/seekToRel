package com.reportai.common.contract;

import java.util.Objects;

/** Bounded, content-addressed evidence pointer; it never contains raw model reasoning. */
public record EvidenceRef(String type, String key, Sha256Digest digest) {
    public EvidenceRef {
        type = text(type, "type", 64);
        key = text(key, "key", 512);
        digest = Objects.requireNonNull(digest, "digest");
    }

    private static String text(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isBlank() || normalized.length() > max
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }
}
