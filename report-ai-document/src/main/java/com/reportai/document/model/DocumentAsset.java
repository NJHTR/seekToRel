package com.reportai.document.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A binary asset referenced by a canonical document.
 *
 * <p>The canonical document stores metadata only. The bytes are kept in object
 * storage and are addressed by {@link #objectKey()}. Keeping the key and
 * checksum in the document makes exports deterministic and allows the object
 * store adapter to verify integrity without trusting a client supplied path.</p>
 */
public record DocumentAsset(String objectKey,
                            String originalFileName,
                            String mediaType,
                            long size,
                            String sha256) {
    private static final int MAX_OBJECT_KEY_LENGTH = 512;
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final Pattern SAFE_SEGMENT = Pattern.compile("^[\\p{L}\\p{N}._@+\\-]+$");
    private static final Pattern MEDIA_TYPE = Pattern.compile(
            "^[A-Za-z0-9!#$&^_.+\\-]+/[A-Za-z0-9!#$&^_.+\\-]+$");
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Set<String> IMAGE_MEDIA_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/bmp", "image/tiff");

    public DocumentAsset {
        objectKey = validateObjectKey(objectKey);
        originalFileName = safeFileName(originalFileName);
        mediaType = validateMediaType(mediaType);
        if (size <= 0) {
            throw new IllegalArgumentException("asset size must be positive");
        }
        sha256 = Objects.requireNonNull(sha256, "sha256").trim().toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("asset sha256 must be a lowercase 64 character hex digest");
        }
    }

    public boolean isImage() {
        return IMAGE_MEDIA_TYPES.contains(mediaType);
    }

    /** Normalizes and validates a user-visible file name without allowing paths. */
    public static String safeFileName(String fileName) {
        Objects.requireNonNull(fileName, "originalFileName");
        String normalized = fileName.strip();
        if (normalized.isEmpty() || normalized.length() > MAX_FILE_NAME_LENGTH
                || normalized.indexOf('\0') >= 0 || normalized.indexOf('/') >= 0
                || normalized.indexOf('\\') >= 0 || normalized.contains("..")
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("asset file name is unsafe");
        }
        return normalized;
    }

    private static String validateObjectKey(String objectKey) {
        Objects.requireNonNull(objectKey, "objectKey");
        String normalized = objectKey.strip();
        if (normalized.isEmpty() || normalized.length() > MAX_OBJECT_KEY_LENGTH
                || !normalized.equals(objectKey) || normalized.startsWith("/")
                || normalized.endsWith("/") || normalized.indexOf('\\') >= 0
                || normalized.indexOf('\0') >= 0
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("asset object key is unsafe");
        }
        for (String segment : normalized.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || !SAFE_SEGMENT.matcher(segment).matches()) {
                throw new IllegalArgumentException("asset object key contains an unsafe path segment");
            }
        }
        return normalized;
    }

    private static String validateMediaType(String mediaType) {
        Objects.requireNonNull(mediaType, "mediaType");
        String normalized = mediaType.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 128 || !normalized.equals(mediaType.strip())
                || !MEDIA_TYPE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("asset media type is invalid");
        }
        return normalized;
    }
}
