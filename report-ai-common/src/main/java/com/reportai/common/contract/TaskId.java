package com.reportai.common.contract;

public record TaskId(String value) implements TypedId {
    public TaskId { value = IdentifierRules.require(value, "taskId"); }
}
