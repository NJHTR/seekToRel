package com.reportai.llm;

public class LlmException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;

    public LlmException(String code, String message) {
        super(message);
        this.code = code;
    }

    public LlmException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
