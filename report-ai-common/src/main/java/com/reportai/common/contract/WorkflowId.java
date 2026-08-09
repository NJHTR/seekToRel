package com.reportai.common.contract;

public record WorkflowId(String value) implements TypedId {
    public WorkflowId { value = IdentifierRules.require(value, "workflowId"); }
}
