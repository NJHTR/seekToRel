package com.reportai.api.report;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Optimistic-lock command for a task status transition. */
public record TaskControlRequest(
        @Min(0) long expectedVersion,
        @NotBlank @Pattern(regexp = "CREATED|PARSING|PLANNING|EXECUTING|REVIEWING|EXPORTING|PAUSED|SUCCEEDED|FAILED|CANCELLED")
        String targetStatus) {
}
