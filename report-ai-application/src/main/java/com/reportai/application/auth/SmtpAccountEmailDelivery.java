package com.reportai.application.auth;

import com.reportai.application.config.properties.ReportAiEmailProperties;
import com.reportai.auth.model.OneTimeTokenPurpose;
import com.reportai.auth.port.AccountEmailDelivery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** SMTP adapter for one-time account verification and password recovery links. */
@Component
@ConditionalOnProperty(prefix = "report-ai.auth.email", name = "enabled", havingValue = "true")
public final class SmtpAccountEmailDelivery implements AccountEmailDelivery {
    private final JavaMailSender sender;
    private final ReportAiEmailProperties properties;

    public SmtpAccountEmailDelivery(JavaMailSender sender, ReportAiEmailProperties properties) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.properties = Objects.requireNonNull(properties, "properties");
        if (properties.from().isBlank()) {
            throw new IllegalArgumentException("report-ai.auth.email.from is required when email is enabled");
        }
    }

    @Override
    public void deliver(String recipient,
                        String displayName,
                        OneTimeTokenPurpose purpose,
                        String rawToken,
                        Instant expiresAt) {
        String normalizedRecipient = required(recipient, "recipient", 320);
        String token = required(rawToken, "rawToken", 256);
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(expiresAt, "expiresAt");

        String action = purpose == OneTimeTokenPurpose.EMAIL_VERIFICATION
                ? "verify your email address" : "reset your ReportAI password";
        String path = purpose == OneTimeTokenPurpose.EMAIL_VERIFICATION
                ? "/auth/verify-email" : "/auth/reset-password";
        String link = properties.baseUrl().replaceAll("/$", "") + path + "?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        String greeting = displayName == null || displayName.isBlank() ? "there" : displayName.trim();
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        long minutes = Math.max(1, remaining.toMinutes());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(normalizedRecipient);
        message.setSubject(properties.subjectPrefix() + " account security");
        message.setText("Hello " + greeting + ",\n\n"
                + "Use the link below to " + action + ":\n" + link + "\n\n"
                + "This link expires in about " + minutes + " minutes and can be used once.\n"
                + "If you did not request this, you can safely ignore this message.\n");
        sender.send(message);
    }

    private static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.trim();
    }
}
