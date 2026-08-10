package com.reportai.recovery;

/** Program validation gate. Implementations must bind the candidate to a fresh branch/checkpoint. */
public interface PrecheckPort {
    PrecheckResult validate(FailureObservation observation, RepairCheckpoint checkpoint, RecoveryPayload candidate);
    record PrecheckResult(boolean accepted, String receiptId, String reason) { public PrecheckResult { if(receiptId==null||receiptId.isBlank()) throw new IllegalArgumentException("receiptId required"); reason=reason==null?"":reason; } }
}
