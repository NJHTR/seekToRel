package com.reportai.storage.objectstore;

import java.util.regex.Pattern;

/** Minimal RFC 6838-style media type validation without provider coupling. */
public final class MediaTypeValidator {
    private static final int MAX_MEDIA_TYPE_LENGTH = 255;
    private static final Pattern MEDIA_TYPE = Pattern.compile(
            "^[A-Za-z0-9!#$&^_.+\\-]+/[A-Za-z0-9!#$&^_.+\\-]+"
                    + "(?:\\s*;\\s*[A-Za-z0-9!#$&^_.+\\-]+=(?:[A-Za-z0-9!#$&^_.+\\-]+|\\\"[^\\\"\\r\\n]*\\\"))*$");

    private MediaTypeValidator() {
    }

    public static String validate(String mediaType) {
        if (mediaType == null || mediaType.isBlank() || mediaType.length() > MAX_MEDIA_TYPE_LENGTH
                || !mediaType.equals(mediaType.strip())
                || mediaType.indexOf('\r') >= 0
                || mediaType.indexOf('\n') >= 0
                || !MEDIA_TYPE.matcher(mediaType).matches()) {
            throw new IllegalArgumentException("contentType is invalid");
        }
        return mediaType;
    }
}
