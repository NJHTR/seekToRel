package com.reportai.llm;

import java.util.Arrays;

/** Provider credentials are never rendered in logs or exception messages. */
public final class SecretValue implements AutoCloseable {
    private char[] value;

    public SecretValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("secret must not be blank");
        }
        this.value = value.toCharArray();
    }

    public synchronized char[] revealCopy() {
        if (value == null) {
            throw new IllegalStateException("secret has been cleared");
        }
        return Arrays.copyOf(value, value.length);
    }

    @Override
    public synchronized String toString() {
        return "[REDACTED]";
    }

    @Override
    public synchronized void close() {
        if (value != null) {
            Arrays.fill(value, '\0');
            value = null;
        }
    }
}
