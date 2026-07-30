package com.reportai.storage;

import java.io.InputStream;

public interface ObjectStorage {
    String put(String objectKey, InputStream data, long contentLength, String contentType);

    /**
     * Stores bytes using a precomputed immutable reference. The implementation
     * still validates the key, media type and declared size before uploading.
     * A caller that requires end-to-end checksum verification should compare the
     * digest while producing the reference and reconcile failed uploads.
     */
    default StoredObject put(StoredObject reference, InputStream data) {
        java.util.Objects.requireNonNull(reference, "reference");
        java.util.Objects.requireNonNull(data, "data");
        final java.security.MessageDigest digest;
        try {
            digest = java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        String persisted = null;
        InputStream digesting = new java.security.DigestInputStream(data, digest);
        try {
            persisted = put(reference.objectKey(), digesting, reference.size(), reference.mediaType());
            if (!reference.objectKey().equals(persisted)) {
                throw new IllegalStateException("object storage changed the requested object key");
            }
            String actual = java.util.HexFormat.of().formatHex(digest.digest());
            if (!reference.sha256().equals(actual)) {
                throw new IllegalStateException("stored object checksum does not match its reference");
            }
            return reference;
        } catch (RuntimeException failure) {
            if (persisted != null) {
                try {
                    delete(persisted);
                } catch (RuntimeException ignored) {
                    // Preserve the original upload or integrity failure.
                }
            }
            throw new IllegalStateException("object upload could not be verified", failure);
        }
    }

    InputStream open(String objectKey);

    void delete(String objectKey);
}
