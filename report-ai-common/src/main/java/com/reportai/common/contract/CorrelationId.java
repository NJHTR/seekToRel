package com.reportai.common.contract;

public record CorrelationId(String value) implements TypedId {
    public CorrelationId { value = IdentifierRules.require(value, "correlationId"); }
}
