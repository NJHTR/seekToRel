package com.reportai.auth.service;

import com.reportai.auth.model.AccountSecurityToken;
import com.reportai.auth.model.OneTimeTokenPurpose;
import com.reportai.auth.port.AccountSecurityTokenRepository;
import com.reportai.auth.token.OneTimeTokenCodec;
import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountSecurityTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");

    @Test
    void issuesHashedTokenAndRevokesPreviousTokenForSamePurpose() {
        InMemoryRepository repository = new InMemoryRepository();
        AccountSecurityTokenService service = service(repository);

        var first = service.issueEmailVerification("user-1", TenantId.of("tenant-1"), "127.0.0.1", "test");
        var second = service.issueEmailVerification("user-1", TenantId.of("tenant-1"), "127.0.0.1", "test");

        assertEquals(43, first.rawToken().length());
        assertEquals(64, first.record().tokenHash().length());
        assertEquals(OneTimeTokenCodec.sha256(first.rawToken()), first.record().tokenHash());
        assertNotEquals(first.rawToken(), first.record().tokenHash());
        assertTrue(first.toString().contains("rawToken=<redacted>"));
        assertFalse(first.toString().contains(first.rawToken()));
        assertEquals(NOW, repository.tokens.get(0).revokedAt());
        assertTrue(second.record().usableAt(NOW));
    }

    @Test
    void purposeIsBoundAndSuccessfulConsumptionIsOneTime() {
        InMemoryRepository repository = new InMemoryRepository();
        AccountSecurityTokenService service = service(repository);
        var issued = service.issuePasswordReset("user-1", TenantId.of("tenant-1"), "", "");

        assertTrue(service.consume(issued.rawToken(), OneTimeTokenPurpose.EMAIL_VERIFICATION).isEmpty());
        assertTrue(service.consume(issued.rawToken(), OneTimeTokenPurpose.PASSWORD_RESET).isPresent());
        assertTrue(service.consume(issued.rawToken(), OneTimeTokenPurpose.PASSWORD_RESET).isEmpty());
        assertEquals(NOW, repository.tokens.get(0).consumedAt());
    }

    @Test
    void malformedCredentialIsFailClosedWithoutRepositoryLookup() {
        InMemoryRepository repository = new InMemoryRepository();
        AccountSecurityTokenService service = service(repository);

        assertTrue(service.consume("not-a-token", OneTimeTokenPurpose.PASSWORD_RESET).isEmpty());
        assertEquals(0, repository.consumeCalls);
    }

    @Test
    void rejectsUnsafeTtlConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new AccountSecurityTokenService(
                new InMemoryRepository(), Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom(),
                Duration.ofSeconds(1), Duration.ofMinutes(30)));
        assertThrows(IllegalArgumentException.class, () -> new AccountSecurityTokenService(
                new InMemoryRepository(), Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom(),
                Duration.ofHours(24), Duration.ofHours(3)));
    }

    private static AccountSecurityTokenService service(InMemoryRepository repository) {
        return new AccountSecurityTokenService(repository, Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureRandom());
    }

    private static final class InMemoryRepository implements AccountSecurityTokenRepository {
        private final List<AccountSecurityToken> tokens = new ArrayList<>();
        private int consumeCalls;

        @Override
        public void replaceActiveAndSave(AccountSecurityToken replacement) {
            String userId = replacement.userId();
            TenantId tenantId = replacement.tenantId();
            OneTimeTokenPurpose purpose = replacement.purpose();
            Instant revokedAt = replacement.createdAt();
            for (int i = 0; i < tokens.size(); i++) {
                AccountSecurityToken token = tokens.get(i);
                if (token.userId().equals(userId) && token.tenantId().equals(tenantId)
                        && token.purpose() == purpose && token.usableAt(revokedAt)) {
                    tokens.set(i, copy(token, token.consumedAt(), revokedAt));
                }
            }
            tokens.add(replacement);
        }

        @Override
        public Optional<AccountSecurityToken> consumeIfUsable(String tokenHash,
                                                               OneTimeTokenPurpose purpose,
                                                               Instant consumedAt) {
            consumeCalls++;
            for (int i = 0; i < tokens.size(); i++) {
                AccountSecurityToken token = tokens.get(i);
                if (token.tokenHash().equals(tokenHash) && token.purpose() == purpose
                        && token.usableAt(consumedAt)) {
                    AccountSecurityToken consumed = copy(token, consumedAt, token.revokedAt());
                    tokens.set(i, consumed);
                    return Optional.of(consumed);
                }
            }
            return Optional.empty();
        }

        private static AccountSecurityToken copy(AccountSecurityToken token, Instant consumedAt,
                                                 Instant revokedAt) {
            return new AccountSecurityToken(token.id(), token.userId(), token.tenantId(), token.purpose(),
                    token.tokenHash(), token.expiresAt(), consumedAt, revokedAt, token.createdAt(),
                    token.requestIp(), token.userAgent());
        }
    }
}
