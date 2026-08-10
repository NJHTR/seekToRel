package com.reportai.recovery;

public interface RecoveryStrategy {
    RecoveryAction action();
    RecoveryPayload execute(FailureObservation observation, RecoveryPayload request, RepairCheckpoint checkpoint);
}
