package com.reportai.recovery;

import java.util.Objects;

/** Immutable isolation identity; every repair must create a fresh pair. */
public record RepairCheckpoint(String branchRef, String checkpointRef, String precheckReceiptId) {
    public RepairCheckpoint {
        branchRef = text(branchRef, "branchRef"); checkpointRef = text(checkpointRef, "checkpointRef"); precheckReceiptId = text(precheckReceiptId, "precheckReceiptId");
    }
    private static String text(String v, String f) { Objects.requireNonNull(v, f); String n=v.strip(); if(n.isBlank()||n.length()>256||n.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(f+" is invalid"); return n; }
}
