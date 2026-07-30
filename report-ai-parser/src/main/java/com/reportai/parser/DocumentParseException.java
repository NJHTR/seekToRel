package com.reportai.parser;

/** Stable adapter exception. The application layer should map this to a generic safe error response. */
public final class DocumentParseException extends RuntimeException {
    public DocumentParseException(String message) {
        super(message);
    }

    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
