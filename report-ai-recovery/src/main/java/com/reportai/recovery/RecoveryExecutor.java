package com.reportai.recovery;

public final class RecoveryExecutor {
    private final PrecheckPort precheck;
    private final java.util.Set<String> usedCheckpoints = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public RecoveryExecutor(PrecheckPort precheck) { this.precheck=java.util.Objects.requireNonNull(precheck,"precheck"); }
    public RecoveryPayload validateAndExecute(FailureObservation observation, RecoveryPayload request, RepairCheckpoint checkpoint, RecoveryStrategy strategy) {
        if(observation==null||request==null||checkpoint==null||strategy==null) throw new NullPointerException("recovery execution input");
        if (!compatible(strategy.action(), request)) throw new RecoveryConflictException("strategy action does not match recovery payload");
        PrecheckPort.PrecheckResult result=precheck.validate(observation,checkpoint,request);
        if(!result.accepted()) throw new RecoveryConflictException("recovery candidate failed program precheck: "+result.reason());
        if (result.receiptId().equals(checkpoint.precheckReceiptId())) throw new RecoveryConflictException("repair must use a fresh precheck receipt");
        String checkpointKey = observation.tenantId().value() + ":" + observation.taskId() + ":" + checkpoint.branchRef() + ":" + checkpoint.checkpointRef();
        if (!usedCheckpoints.add(checkpointKey)) throw new RecoveryConflictException("repair branch/checkpoint was already used");
        return strategy.execute(observation,request,new RepairCheckpoint(checkpoint.branchRef(),checkpoint.checkpointRef(),result.receiptId()));
    }
    private static boolean compatible(RecoveryAction action, RecoveryPayload payload) {
        return switch (action) {
            case SUBMIT_INPUT -> payload instanceof InputSubmission;
            case APPROVE_DOWNLOAD, REJECT_DOWNLOAD -> payload instanceof DownloadDecision;
            case DIAGNOSE_CODE -> payload instanceof CodeDiagnosisRequest;
            case REPAIR_DEPENDENCY -> payload instanceof DependencyRepairRequest || payload instanceof DependencyRepairPlan;
            case RUN_OCR -> payload instanceof OcrRequest;
            case EXTRACT_CHART -> payload instanceof ChartExtractionRequest;
            case RESOLVE_CONFLICT -> payload instanceof ConflictResolution;
            default -> true;
        };
    }
}
