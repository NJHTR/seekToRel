package com.reportai.auth.service;

import com.reportai.auth.model.AccountSecurityToken;
import com.reportai.auth.model.IssuedAccountSecurityToken;
import com.reportai.auth.model.OneTimeTokenPurpose;
import com.reportai.auth.port.AccountSecurityTokenRepository;
import com.reportai.auth.token.OneTimeTokenCodec;
import com.reportai.common.tenant.TenantId;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Framework-independent lifecycle service for verification and password-recovery credentials.
 * Delivery (email, queue, or another provider) remains outside this module.
 */
public final class AccountSecurityTokenService {
    public static final Duration DEFAULT_EMAIL_VERIFICATION_TTL = Duration.ofHours(24);
    public static final Duration DEFAULT_PASSWORD_RESET_TTL = Duration.ofMinutes(30);

    private final AccountSecurityTokenRepository repository;
    private final Clock clock;
    private final SecureRandom random;
    private final Duration emailVerificationTtl;
    private final Duration passwordResetTtl;

    public AccountSecurityTokenService(AccountSecurityTokenRepository repository,
                                       Clock clock,
                                       SecureRandom random,
                                       Duration emailVerificationTtl,
                                       Duration passwordResetTtl) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
        this.emailVerificationTtl = boundedTtl(emailVerificationTtl, "emailVerificationTtl",
                Duration.ofMinutes(5), Duration.ofDays(7));
        this.passwordResetTtl = boundedTtl(passwordResetTtl, "passwordResetTtl",
                Duration.ofMinutes(5), Duration.ofHours(2));
    }

    public AccountSecurityTokenService(AccountSecurityTokenRepository repository, Clock clock,
                                       SecureRandom random) {
        this(repository, clock, random, DEFAULT_EMAIL_VERIFICATION_TTL, DEFAULT_PASSWORD_RESET_TTL);
    }

    public IssuedAccountSecurityToken issueEmailVerification(String userId,
                                                             TenantId tenantId,
                                                             String requestIp,
                                                             String userAgent) {
        return issue(userId, tenantId, OneTimeTokenPurpose.EMAIL_VERIFICATION,
                emailVerificationTtl, requestIp, userAgent);
    }

    public IssuedAccountSecurityToken issuePasswordReset(String userId,
                                                         TenantId tenantId,
                                                         String requestIp,
                                                         String userAgent) {
        return issue(userId, tenantId, OneTimeTokenPurpose.PASSWORD_RESET,
                passwordResetTtl, requestIp, userAgent);
    }

    /**
     * Consumes a token once.  The repository must enforce the one-time guarantee atomically.
     * Invalid/malformed tokens return an empty result and do not expose lookup details.
     */
    public Optional<AccountSecurityToken> consume(String rawToken, OneTimeTokenPurpose purpose) {
        Objects.requireNonNull(purpose, "purpose");
        final String tokenHash;
        try {
            tokenHash = OneTimeTokenCodec.sha256(rawToken);
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
        return repository.consumeIfUsable(tokenHash, purpose, Instant.now(clock));
    }

    private IssuedAccountSecurityToken issue(String userId,
                                             TenantId tenantId,
                                             OneTimeTokenPurpose purpose,
                                             Duration ttl,
                                             String requestIp,
                                             String userAgent) {
        Objects.requireNonNull(tenantId, "tenantId");
        String normalizedUserId = required(userId, "userId", 64);
        Instant now = Instant.now(clock);
        String rawToken = OneTimeTokenCodec.generate(random);
        AccountSecurityToken token = new AccountSecurityToken(
                java.util.UUID.randomUUID().toString(), normalizedUserId, tenantId, purpose,
                OneTimeTokenCodec.sha256(rawToken), now.plus(ttl), null, null, now, requestIp, userAgent);
        repository.replaceActiveAndSave(token);
        return new IssuedAccountSecurityToken(token, rawToken);
    }

    private static Duration boundedTtl(Duration ttl, String name, Duration minimum, Duration maximum) {
        if (ttl == null || ttl.compareTo(minimum) < 0 || ttl.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
        return ttl;
    }

    private static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.trim();
    }
}
