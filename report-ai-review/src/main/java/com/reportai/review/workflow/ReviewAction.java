package com.reportai.review.workflow;

public enum ReviewAction {
    /** Kept for source compatibility with the pre-T020 draft; creation is done by the repository. */
    PRECHECK,
    SUBMIT_FIRST_REVIEW,
    SUBMIT_ACCEPTANCE,
    SUBMIT_REJECTION,
    SUBMIT_SECOND_REVIEW,
    REQUEST_RECOVERY,
    COMPLETE_RECOVERY,
    REENTER_FIRST_REVIEW,
    /** Terminal operator action. A bounded workflow uses EXHAUSTED instead. */
    ESCALATE
}
