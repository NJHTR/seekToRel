package com.reportai.recovery;

public enum RecoveryState {
    PENDING, RUNNING, WAITING_FOR_INPUT, WAITING_FOR_APPROVAL,
    COMPLETED, FAILED, ESCALATED, EXHAUSTED
}
