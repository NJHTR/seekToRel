package com.reportai.common.contract;

public record TraceId(String value) implements TypedId {
    public TraceId { value = IdentifierRules.require(value, "traceId"); }
}
