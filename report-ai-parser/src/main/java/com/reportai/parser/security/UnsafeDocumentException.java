package com.reportai.parser.security;

/** Sanitised exception for rejected archives; callers must not expose the cause to clients. */
public final class UnsafeDocumentException extends RuntimeException {
    public UnsafeDocumentException(String message) {
        super(message);
    }

    public UnsafeDocumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
