package com.reportai.auth.port;

import com.reportai.auth.model.OneTimeTokenPurpose;

import java.time.Instant;

/** Delivery boundary for account verification and password recovery messages. */
public interface AccountEmailDelivery {
    /**
     * Delivers a one-time credential. Implementations must never log or persist {@code rawToken}.
     */
    void deliver(String recipient,
                 String displayName,
                 OneTimeTokenPurpose purpose,
                 String rawToken,
                 Instant expiresAt);
}
