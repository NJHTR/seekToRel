package com.reportai.auth.model;

/** Allow-list of security-relevant account lifecycle actions. */
public enum AccountAuditAction {
    EMAIL_VERIFICATION_REQUESTED,
    EMAIL_VERIFIED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED,
    ACCOUNT_DISABLED,
    ACCOUNT_ENABLED,
    ROLE_GRANTED,
    ROLE_REVOKED
}
