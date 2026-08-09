package com.reportai.runtime;

/** Safe, actionable failure returned when an administrator catalog cannot resolve a request. */
public final class EnvironmentCatalogException extends IllegalArgumentException {
    public enum Code {
        LANGUAGE_NOT_APPROVED,
        DEPENDENCY_UNPINNED,
        DEPENDENCY_NOT_APPROVED,
        CATALOG_INVALID,
        CATALOG_SIGNATURE_INVALID
    }

    private final Code code;

    public EnvironmentCatalogException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
