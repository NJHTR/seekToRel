package com.reportai.common.contract;

public record RunId(String value) implements TypedId {
    public RunId { value = IdentifierRules.require(value, "runId"); }
}
