package com.reportai.scheduler;

/**
 * Signals a permanent task failure that cannot be repaired by another
 * scheduler attempt (for example, invalid input or missing mandatory
 * configuration).
 */
public class NonRetryableTaskException extends RuntimeException {
    public NonRetryableTaskException(String message) {
        super(message);
    }

    public NonRetryableTaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
