package com.reportai.auth.port;

import com.reportai.auth.model.RefreshTokenRecord;
import com.reportai.auth.model.UserAccount;

import java.time.Instant;
import java.util.Optional;

/** Persistence port for first-party account and refresh-token state. */
public interface AccountRepository {
    void createTenantAndAccount(UserAccount account, String tenantName);

    Optional<UserAccount> findByEmail(String normalizedEmail);

    Optional<UserAccount> findById(String userId);

    void updateLastLogin(String userId, Instant lastLoginAt);

    void saveRefreshToken(RefreshTokenRecord refreshToken);

    Optional<RefreshTokenRecord> findRefreshTokenByHash(String tokenHash);

    boolean revokeRefreshToken(String tokenId, Instant revokedAt);

    int revokeAllRefreshTokens(String userId, Instant revokedAt);
}
