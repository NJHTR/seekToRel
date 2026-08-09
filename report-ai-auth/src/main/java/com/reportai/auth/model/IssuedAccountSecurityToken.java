package com.reportai.auth.model;

import java.util.Objects;

/** Raw one-time credential plus its safe persisted representation. */
public record IssuedAccountSecurityToken(AccountSecurityToken record, String rawToken) {
    public IssuedAccountSecurityToken {
        record = Objects.requireNonNull(record, "record");
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("rawToken is invalid");
        }
    }

    /** Prevent accidental log statements from emitting a live recovery credential. */
    @Override
    public String toString() {
        return "IssuedAccountSecurityToken[record=" + record.id() + ", purpose="
                + record.purpose() + ", expiresAt=" + record.expiresAt() + ", rawToken=<redacted>]";
    }
}
