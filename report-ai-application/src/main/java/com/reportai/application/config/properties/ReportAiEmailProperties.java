package com.reportai.application.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Externalized settings for account verification and recovery email delivery. */
@ConfigurationProperties(prefix = "report-ai.auth.email")
public record ReportAiEmailProperties(
        boolean enabled,
        String from,
        String baseUrl,
        String subjectPrefix) {

    public ReportAiEmailProperties {
        from = value(from, "");
        baseUrl = value(baseUrl, "http://127.0.0.1:5173");
        subjectPrefix = value(subjectPrefix, "ReportAI");
        if (from.length() > 320 || baseUrl.length() > 512 || subjectPrefix.length() > 128) {
            throw new IllegalArgumentException("account email configuration is too long");
        }
        if (containsLineBreak(from) || containsLineBreak(baseUrl) || containsLineBreak(subjectPrefix)) {
            throw new IllegalArgumentException("account email configuration contains a line break");
        }
    }

    private static String value(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    private static boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }
}
