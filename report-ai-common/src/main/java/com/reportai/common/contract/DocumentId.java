package com.reportai.common.contract;

public record DocumentId(String value) implements TypedId {
    public DocumentId { value = IdentifierRules.require(value, "documentId"); }
}
