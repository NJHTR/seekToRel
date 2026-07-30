package com.reportai.common.api;

import java.time.Instant;

/** Stable API envelope. Public messages must not contain stack traces or secrets. */
public final class Result<T> {
    private final boolean success;
    private final T data;
    private final String code;
    private final String message;
    private final String traceId;
    private final Instant timestamp;

    private Result(boolean success, T data, String code, String message, String traceId) {
        this.success = success;
        this.data = data;
        this.code = code;
        this.message = message;
        this.traceId = traceId;
        this.timestamp = Instant.now();
    }

    public static <T> Result<T> success(T data, String traceId) {
        return new Result<>(true, data, "OK", "OK", traceId);
    }

    public static <T> Result<T> failure(ErrorCode errorCode, String traceId) {
        return new Result<>(false, null, errorCode.getCode(), errorCode.getMessage(), traceId);
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
