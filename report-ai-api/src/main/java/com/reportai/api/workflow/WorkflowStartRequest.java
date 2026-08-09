package com.reportai.api.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WorkflowStartRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9._:-]{1,128}") String definitionId,
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9._:-]{1,128}") String taskId) {
}
