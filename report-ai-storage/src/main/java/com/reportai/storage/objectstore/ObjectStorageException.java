package com.reportai.storage.objectstore;

/**
 * Runtime failure raised by an object storage adapter.
 *
 * <p>The public message is deliberately generic. Provider responses can
 * contain endpoints, request identifiers or implementation details and must
 * not be exposed directly to API callers.</p>
 */
public final class ObjectStorageException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ObjectStorageException(String message) {
        super(message);
    }

    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
