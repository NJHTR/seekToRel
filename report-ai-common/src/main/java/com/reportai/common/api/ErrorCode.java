package com.reportai.common.api;

public enum ErrorCode {
    INVALID_ARGUMENT("INVALID_ARGUMENT", "请求参数不合法"),
    UNAUTHORIZED("UNAUTHORIZED", "未认证"),
    FORBIDDEN("FORBIDDEN", "无权执行此操作"),
    NOT_FOUND("NOT_FOUND", "资源不存在"),
    CONFLICT("CONFLICT", "请求与当前状态冲突"),
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "重复请求的参数不一致"),
    RATE_LIMITED("RATE_LIMITED", "请求过于频繁"),
    INTERNAL_ERROR("INTERNAL_ERROR", "系统内部错误");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
