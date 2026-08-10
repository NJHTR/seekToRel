package com.reportai.recovery;

/** Stable UI-facing status, deliberately independent from scheduler states. */
public enum RecoveryStatus {
    RECOVERING,
    AWAITING_INPUT,
    DEGRADED,
    POLICY_BLOCKED,
    FINAL_FAILURE
}
