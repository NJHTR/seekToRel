package com.reportai.common.contract;

public record CommandId(String value) implements TypedId {
    public CommandId { value = IdentifierRules.require(value, "commandId"); }
}
