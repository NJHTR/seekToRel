package com.reportai.auth.port;

import com.reportai.auth.model.AccountSecurityToken;
import com.reportai.auth.model.OneTimeTokenPurpose;
import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.Optional;

/**
 * Atomic persistence boundary for account verification and recovery credentials.
 *
 * <p>{@link #consumeIfUsable(String, OneTimeTokenPurpose, Instant)} must perform the consume as a
 * single conditional update (or an equivalent serializable operation).  A read followed by a
 * separate update is not sufficient because two concurrent requests could otherwise both reset a
 * password or verify an address.</p>
 */
public interface AccountSecurityTokenRepository {
    /**
     * Atomically invalidates outstanding credentials for the same account/purpose and stores the
     * replacement.  Implementations should use one database transaction so a failed insert cannot
     * leave an account with all recovery credentials revoked.
     */
    void replaceActiveAndSave(AccountSecurityToken token);

    /** Atomically consumes a matching, unexpired, unrevoked credential. */
    Optional<AccountSecurityToken> consumeIfUsable(String tokenHash,
                                                    OneTimeTokenPurpose purpose,
                                                    Instant consumedAt);
}
