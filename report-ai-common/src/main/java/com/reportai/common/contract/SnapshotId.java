package com.reportai.common.contract;

public record SnapshotId(String value) implements TypedId {
    public SnapshotId { value = IdentifierRules.require(value, "snapshotId"); }
}
