package com.reportai.common.contract;

public record EvaluationId(String value) implements TypedId {
    public EvaluationId { value = IdentifierRules.require(value, "evaluationId"); }
}
