package com.reportai.recovery;

public enum FailureRoute {
    AUTOMATIC_REPAIR,
    WAIT_FOR_INPUT,
    WAIT_FOR_DOWNLOAD_APPROVAL,
    POLICY_BLOCKED,
    ESCALATE,
    FINAL_FAILURE
}
