package com.reportai.recovery;

/** Program validation gate. Implementations must bind the candidate to a fresh branch/checkpoint. */
public interface PrecheckPort {
    PrecheckResult validate(FailureObservation observation, RepairCheckpoint checkpoint, RecoveryPayload candidate);
    record PrecheckResult(boolean accepted, String receiptId, String reason, String branchRef, String checkpointRef) {
        public PrecheckResult { if(receiptId==null||receiptId.isBlank()) throw new IllegalArgumentException("receiptId required"); reason=reason==null?"":reason; branchRef=branchRef==null?"":branchRef.strip(); checkpointRef=checkpointRef==null?"":checkpointRef.strip(); if(branchRef.length()>256||checkpointRef.length()>256) throw new IllegalArgumentException("checkpoint identity is too large"); }
        public PrecheckResult(boolean accepted, String receiptId, String reason) { this(accepted,receiptId,reason,"",""); }
    }
}
