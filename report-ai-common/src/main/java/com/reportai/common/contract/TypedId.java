package com.reportai.common.contract;

/** Marker for tenant-safe opaque identifiers used by cross-module contracts. */
public interface TypedId {
    String value();
}
