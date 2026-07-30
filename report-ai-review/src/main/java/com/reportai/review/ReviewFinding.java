package com.reportai.review;

public record ReviewFinding(Severity severity, String ruleId, String message, String location) {
    public ReviewFinding {
        if (severity == null || ruleId == null || ruleId.isBlank()
                || message == null || message.isBlank()) {
            throw new IllegalArgumentException("severity, ruleId and message are required");
        }
        location = location == null ? "document" : location;
    }

    public enum Severity { INFO, WARNING, ERROR }
}
