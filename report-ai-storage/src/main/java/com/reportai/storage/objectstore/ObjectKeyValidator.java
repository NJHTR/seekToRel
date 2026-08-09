package com.reportai.storage.objectstore;

import java.util.regex.Pattern;

/** Validates object keys before they reach an S3-compatible provider. */
public final class ObjectKeyValidator {
    private static final int MAX_KEY_LENGTH = 512;
    private static final Pattern SAFE_SEGMENT = Pattern.compile("^[\\p{L}\\p{N}._@+\\-]+$");

    private ObjectKeyValidator() {
    }

    public static String validate(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.length() > MAX_KEY_LENGTH
                || !objectKey.equals(objectKey.strip())
                || objectKey.startsWith("/")
                || objectKey.endsWith("/")
                || objectKey.indexOf('\\') >= 0
                || objectKey.indexOf('\u0000') >= 0
                || objectKey.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("objectKey is invalid");
        }

        String[] segments = objectKey.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || !SAFE_SEGMENT.matcher(segment).matches()) {
                throw new IllegalArgumentException("objectKey contains an unsafe path segment");
            }
        }
        return objectKey;
    }
}
