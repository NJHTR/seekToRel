package com.reportai.auth.model;

/** Lifecycle states for a local account. */
public enum AccountStatus {
    /** Registration has completed but the address has not yet been verified. */
    PENDING_EMAIL,
    ACTIVE,
    LOCKED,
    DISABLED
}
