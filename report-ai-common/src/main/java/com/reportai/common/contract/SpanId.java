package com.reportai.common.contract;

public record SpanId(String value) implements TypedId {
    public SpanId { value = IdentifierRules.require(value, "spanId"); }
}
