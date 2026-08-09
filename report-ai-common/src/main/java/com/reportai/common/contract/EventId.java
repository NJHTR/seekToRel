package com.reportai.common.contract;

public record EventId(String value) implements TypedId {
    public EventId { value = IdentifierRules.require(value, "eventId"); }
}
