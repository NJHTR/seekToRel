package com.reportai.common.error;

/** Stable recovery routing classes shared by scheduler, Agent and API layers. */
public enum FailureClass {
    PROGRAM,
    CONTRACT,
    INPUT_MISSING,
    DATA,
    DEPENDENCY,
    CODE,
    OCR,
    RESOURCE,
    POLICY,
    INFRASTRUCTURE,
    AUTHENTICATION,
    AUTHORIZATION,
    CONFLICT
}
