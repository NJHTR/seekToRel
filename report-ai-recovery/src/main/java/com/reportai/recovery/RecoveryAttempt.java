package com.reportai.recovery;

import com.reportai.common.contract.InputSnapshot;
import com.reportai.common.tenant.TenantId;
import java.util.Objects;

public record RecoveryAttempt(String recoveryId, TenantId tenantId, String taskId, String operation,
                              InputSnapshot sourceSnapshot, String failureFingerprint, com.reportai.common.error.FailureClass failureClass,
                              RecoveryState state, int attempt, int maxAttempts, RepairCheckpoint checkpoint,
                              long version, RecoveryDecision decision) {
    public RecoveryAttempt {
        recoveryId = text(recoveryId,"recoveryId"); tenantId=Objects.requireNonNull(tenantId,"tenantId"); taskId=text(taskId,"taskId"); operation=text(operation,"operation");
        sourceSnapshot=Objects.requireNonNull(sourceSnapshot,"sourceSnapshot"); if(!tenantId.equals(sourceSnapshot.tenantId())) throw new IllegalArgumentException("snapshot tenant mismatch");
        failureFingerprint=text(failureFingerprint,"failureFingerprint"); failureClass=Objects.requireNonNull(failureClass,"failureClass"); state=Objects.requireNonNull(state,"state");
        if(attempt<0||maxAttempts<0||attempt>maxAttempts) throw new IllegalArgumentException("invalid recovery budget");
        if(version<0) throw new IllegalArgumentException("version must not be negative"); decision=Objects.requireNonNull(decision,"decision");
        if(state==RecoveryState.RUNNING||state==RecoveryState.COMPLETED) { if (checkpoint == null) throw new IllegalArgumentException("checkpoint required for active recovery"); }
    }
    public static RecoveryAttempt create(String id, FailureObservation observation, FailureClassification classification, int maxAttempts) {
        if(maxAttempts<1||maxAttempts>20) throw new IllegalArgumentException("maxAttempts out of bounds");
        return new RecoveryAttempt(id, observation.tenantId(), observation.taskId(), observation.operation(), observation.sourceSnapshot(), observation.fingerprint(), classification.failureClass(), RecoveryState.PENDING, 0, maxAttempts, null, 0, decisionFor(classification));
    }
    public RecoveryStatus uiStatus() { if (decision == RecoveryDecision.POLICY_BLOCKED) return RecoveryStatus.POLICY_BLOCKED; return switch(state) { case PENDING, RUNNING -> RecoveryStatus.RECOVERING; case WAITING_FOR_INPUT, WAITING_FOR_APPROVAL -> RecoveryStatus.AWAITING_INPUT; case COMPLETED -> RecoveryStatus.DEGRADED; case FAILED, ESCALATED, EXHAUSTED -> RecoveryStatus.FINAL_FAILURE; }; }
    static RecoveryDecision decisionFor(FailureClassification c) { return switch(c.route()) { case AUTOMATIC_REPAIR -> RecoveryDecision.RETRY_AUTOMATIC; case WAIT_FOR_INPUT -> RecoveryDecision.WAITING_FOR_INPUT; case WAIT_FOR_DOWNLOAD_APPROVAL -> RecoveryDecision.WAITING_FOR_DOWNLOAD_APPROVAL; case POLICY_BLOCKED -> RecoveryDecision.POLICY_BLOCKED; case ESCALATE -> RecoveryDecision.ESCALATE; case FINAL_FAILURE -> RecoveryDecision.EXHAUSTED; }; }
    RecoveryAttempt next(RecoveryState nextState, RecoveryDecision nextDecision, RepairCheckpoint nextCheckpoint, int nextAttempt) { return new RecoveryAttempt(recoveryId,tenantId,taskId,operation,sourceSnapshot,failureFingerprint,failureClass,nextState,nextAttempt,maxAttempts,nextCheckpoint,version+1,nextDecision); }
    private static String text(String v,String f){Objects.requireNonNull(v,f);String n=v.strip();if(n.isBlank()||n.length()>256||n.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException(f+" is invalid");return n;}
}
