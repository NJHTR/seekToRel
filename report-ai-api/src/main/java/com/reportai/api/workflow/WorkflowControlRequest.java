package com.reportai.api.workflow;

import jakarta.validation.constraints.Min;

public record WorkflowControlRequest(@Min(0) long expectedVersion) {
}
