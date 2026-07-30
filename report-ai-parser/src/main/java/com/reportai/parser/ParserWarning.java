package com.reportai.parser;

import java.util.Objects;

/** Non-fatal parser diagnostic. It is safe to expose to callers after sanitisation. */
public record ParserWarning(String code, String message) {
    public ParserWarning {
        code = Objects.requireNonNull(code, "code").trim();
        message = Objects.requireNonNull(message, "message").trim();
        if (code.isEmpty() || message.isEmpty() || message.length() > 500) {
            throw new IllegalArgumentException("invalid parser warning");
        }
    }
}
