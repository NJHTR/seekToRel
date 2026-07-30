package com.reportai.storage.objectstore;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable configuration required by an S3-compatible object store.
 *
 * <p>This type is a configuration port. The application layer may bind
 * external configuration to it without making the storage adapter depend on
 * Spring Boot configuration classes.</p>
 */
public record ObjectStorageProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String region,
        boolean pathStyleAccess,
        long maxObjectSizeBytes) {

    private static final Pattern BUCKET_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$");
    private static final long MAX_S3_OBJECT_SIZE_BYTES = 5_497_558_138_880L;

    public ObjectStorageProperties {
        endpoint = validateEndpoint(endpoint);
        accessKey = requireText(accessKey, "accessKey");
        secretKey = requireText(secretKey, "secretKey");
        bucket = validateBucket(bucket);
        region = region == null || region.isBlank() ? "us-east-1" : region.strip();
        if (region.length() > 63 || region.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("region is invalid");
        }
        if (maxObjectSizeBytes <= 0 || maxObjectSizeBytes > MAX_S3_OBJECT_SIZE_BYTES) {
            throw new IllegalArgumentException("maxObjectSizeBytes must be between 1 byte and 5 TiB");
        }
    }

    private static String validateEndpoint(String value) {
        String endpoint = requireText(value, "endpoint");
        final URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("endpoint is invalid", exception);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("endpoint must be an http(s) origin without credentials or path");
        }
        return endpoint;
    }

    private static String validateBucket(String value) {
        String bucket = requireText(value, "bucket");
        if (bucket.length() > 63
                || !BUCKET_PATTERN.matcher(bucket).matches()
                || bucket.contains("..")
                || bucket.contains(".-")
                || bucket.contains("-.")) {
            throw new IllegalArgumentException("bucket name is invalid");
        }
        return bucket;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() != value.length()
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " must be a non-blank safe value");
        }
        return normalized;
    }

    /** Do not expose accessKey/secretKey through logs. */
    @Override
    public String toString() {
        return "ObjectStorageProperties[endpoint=" + endpoint
                + ", bucket=" + bucket
                + ", region=" + region
                + ", pathStyleAccess=" + pathStyleAccess
                + ", maxObjectSizeBytes=" + maxObjectSizeBytes + "]";
    }
}
