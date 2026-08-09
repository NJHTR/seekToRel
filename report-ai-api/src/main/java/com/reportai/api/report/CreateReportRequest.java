package com.reportai.api.report;

/** Boundary DTO. The controller should validate it before calling an application service. */
public record CreateReportRequest(String documentObjectKey, String idempotencyKey) {
}
