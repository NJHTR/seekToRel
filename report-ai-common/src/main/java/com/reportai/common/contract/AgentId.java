package com.reportai.common.contract;

public record AgentId(String value) implements TypedId {
    public AgentId { value = IdentifierRules.require(value, "agentId"); }
}
