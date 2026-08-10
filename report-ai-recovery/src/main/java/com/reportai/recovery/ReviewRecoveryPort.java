package com.reportai.recovery;
public interface ReviewRecoveryPort {
    void requestRecovery(String recoveryId, String failureFingerprint);
    void completeRecovery(String recoveryId, String evaluationId);
    void reenterFirstReview(String recoveryId, String evaluationId);
}
