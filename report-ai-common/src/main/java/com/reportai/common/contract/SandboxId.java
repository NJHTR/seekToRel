package com.reportai.common.contract;

public record SandboxId(String value) implements TypedId {
    public SandboxId { value = IdentifierRules.require(value, "sandboxId"); }
}
