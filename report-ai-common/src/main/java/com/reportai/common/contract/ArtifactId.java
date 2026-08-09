package com.reportai.common.contract;

public record ArtifactId(String value) implements TypedId {
    public ArtifactId { value = IdentifierRules.require(value, "artifactId"); }
}
