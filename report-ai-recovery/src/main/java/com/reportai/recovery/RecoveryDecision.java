package com.reportai.recovery;

public enum RecoveryDecision {
    RETRY_AUTOMATIC,
    WAITING_FOR_INPUT,
    WAITING_FOR_DOWNLOAD_APPROVAL,
    COMPLETE,
    REENTER_FIRST_REVIEW,
    POLICY_BLOCKED,
    ESCALATE,
    EXHAUSTED
}
