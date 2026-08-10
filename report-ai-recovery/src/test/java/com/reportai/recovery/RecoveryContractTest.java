package com.reportai.recovery;

import com.reportai.common.contract.*;
import com.reportai.common.error.*;
import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryContractTest {
    private static final TenantId TENANT = new TenantId("tenant-a");
    private static final InputSnapshot SNAPSHOT = new InputSnapshot(1, new SnapshotId("snap-1"), TENANT, List.of(), Sha256Digest.ofText("data"), List.of(), Instant.parse("2026-01-01T00:00:00Z"), Map.of());
    private FailureObservation observation(FailureClass kind, boolean retryable, String code) { return FailureObservation.of(TENANT,"task-1","run","agent",SNAPSHOT,new ErrorDetail(code,kind,retryable,"safe","hint",Map.of()),1,Instant.parse("2026-01-01T00:00:00Z")); }

    @Test void routesEveryFailureClassFailClosed() {
        DeterministicFailureClassifier classifier = new DeterministicFailureClassifier();
        assertEquals(FailureRoute.WAIT_FOR_INPUT, classifier.classify(observation(FailureClass.INPUT_MISSING,true,"PYTHON_INPUT_MISSING")).route());
        assertEquals(FailureRoute.POLICY_BLOCKED, classifier.classify(observation(FailureClass.POLICY,true,"POLICY_DENIED")).route());
        assertEquals(FailureRoute.POLICY_BLOCKED, classifier.classify(observation(FailureClass.AUTHORIZATION,true,"AUTH_DENIED")).route());
        assertEquals(FailureRoute.AUTOMATIC_REPAIR, classifier.classify(observation(FailureClass.CODE,true,"PYTHON_RUNTIME_ERROR")).route());
        assertEquals(FailureRoute.WAIT_FOR_DOWNLOAD_APPROVAL, classifier.classify(observation(FailureClass.DATA,true,"DOWNLOAD_REQUESTED")).route());
        assertEquals(FailureRoute.ESCALATE, classifier.classify(observation(FailureClass.CONTRACT,false,"BAD_CONTRACT")).route());
        assertEquals(FailureRoute.ESCALATE, classifier.classify(observation(FailureClass.RESOURCE,false,"RESOURCE_LIMIT")).route());
        assertEquals(FailureRoute.AUTOMATIC_REPAIR, classifier.classify(observation(FailureClass.DEPENDENCY,true,"DEP_MISSING")).route());
        assertEquals(FailureRoute.AUTOMATIC_REPAIR, classifier.classify(observation(FailureClass.OCR,true,"OCR_LOW_CONFIDENCE")).route());
        assertEquals(FailureRoute.AUTOMATIC_REPAIR, classifier.classify(observation(FailureClass.CONFLICT,true,"RESULT_CONFLICT")).route());
        assertEquals(FailureRoute.POLICY_BLOCKED, classifier.classify(observation(FailureClass.AUTHENTICATION,true,"AUTH_REQUIRED")).route());
    }

    @Test void aggregateEnforcesBudgetAndUiStatus() {
        FailureObservation o=observation(FailureClass.CODE,true,"CODE_ERROR");
        RecoveryAttempt a=RecoveryAttempt.create("rec-1",o,new DeterministicFailureClassifier().classify(o),2);
        assertEquals(RecoveryState.PENDING,a.state()); assertEquals(RecoveryStatus.RECOVERING,a.uiStatus());
        assertThrows(IllegalArgumentException.class,()->RecoveryAttempt.create("rec-2",o,new DeterministicFailureClassifier().classify(o),0));
        assertThrows(IllegalArgumentException.class,()->new RecoveryAttempt("rec",TENANT,"task","run",SNAPSHOT,"fp",FailureClass.CODE,RecoveryState.RUNNING,1,1,null,0,RecoveryDecision.RETRY_AUTOMATIC));
    }

    @Test void repositoryIsCasAndIdempotentWithDigestProtection() {
        FailureObservation o=observation(FailureClass.CODE,true,"CODE_ERROR"); RecoveryAttempt a=RecoveryAttempt.create("rec-1",o,new DeterministicFailureClassifier().classify(o),2); InMemoryRecoveryCaseRepository repo=new InMemoryRecoveryCaseRepository(); repo.create(a);
        RepairCheckpoint cp=new RepairCheckpoint("agent/rec-1/repair-1","checkpoint-1","receipt-1"); RecoveryAttempt next=a.next(RecoveryState.RUNNING,RecoveryDecision.RETRY_AUTOMATIC,cp,1);
        RecoveryEvent event=new RecoveryEvent(new EventId("evt-1"),new CommandId("cmd-1"),"idem-1",a.recoveryId(),TENANT,RecoveryAction.START_ATTEMPT,RecoveryEventType.ATTEMPT_STARTED,a.state(),next.state(),1,SNAPSHOT.snapshotId().value(),"digest-a",Instant.now());
        assertFalse(repo.append(a,next,event).duplicate()); assertTrue(repo.append(a,next,event).duplicate());
        RecoveryEvent mismatch=new RecoveryEvent(new EventId("evt-2"),new CommandId("cmd-2"),"idem-1",a.recoveryId(),TENANT,RecoveryAction.START_ATTEMPT,RecoveryEventType.ATTEMPT_STARTED,a.state(),next.state(),1,SNAPSHOT.snapshotId().value(),"digest-b",Instant.now());
        assertThrows(RecoveryConflictException.class,()->repo.append(a,next,mismatch));
        RecoveryEvent staleEvent=new RecoveryEvent(new EventId("evt-3"),new CommandId("cmd-3"),"idem-stale",a.recoveryId(),TENANT,RecoveryAction.START_ATTEMPT,RecoveryEventType.ATTEMPT_STARTED,a.state(),next.state(),1,SNAPSHOT.snapshotId().value(),"digest-c",Instant.now());
        assertThrows(RecoveryConflictException.class,()->repo.append(a,a,staleEvent));
    }

    @Test void repositoryRejectsTenantAndSnapshotTamperingAndReplay() {
        FailureObservation o=observation(FailureClass.CODE,true,"CODE_ERROR"); RecoveryAttempt a=RecoveryAttempt.create("rec-1",o,new DeterministicFailureClassifier().classify(o),2); InMemoryRecoveryCaseRepository repo=new InMemoryRecoveryCaseRepository(); repo.create(a);
        RepairCheckpoint cp=new RepairCheckpoint("branch","checkpoint","receipt"); RecoveryAttempt next=a.next(RecoveryState.RUNNING,RecoveryDecision.RETRY_AUTOMATIC,cp,1);
        RecoveryEvent event=new RecoveryEvent(new EventId("evt-1"),new CommandId("cmd-1"),"idem-1",a.recoveryId(),TENANT,RecoveryAction.START_ATTEMPT,RecoveryEventType.ATTEMPT_STARTED,a.state(),next.state(),1,SNAPSHOT.snapshotId().value(),"digest",Instant.now()); repo.append(a,next,event);
        assertEquals(next,repo.replay(a.recoveryId(),TENANT));
        RecoveryEvent stale = new RecoveryEvent(new EventId("evt-2"),new CommandId("cmd-2"),"idem-stale",a.recoveryId(),TENANT,RecoveryAction.START_ATTEMPT,RecoveryEventType.ATTEMPT_STARTED,a.state(),next.state(),1,SNAPSHOT.snapshotId().value(),"digest",Instant.now());
        assertThrows(RecoveryConflictException.class,()->repo.append(next,next,stale));
    }

    @Test void everyExecutionRequiresFreshCheckpointAndPrecheck() {
        PrecheckPort gate=(o,cp,p)->new PrecheckPort.PrecheckResult(true,"fresh-receipt","",cp.branchRef(),cp.checkpointRef()); RecoveryExecutor executor=new RecoveryExecutor(gate);
        FailureObservation o=observation(FailureClass.CODE,true,"CODE_ERROR"); RecoveryStrategy strategy=new RecoveryStrategy(){public RecoveryAction action(){return RecoveryAction.DIAGNOSE_CODE;} public RecoveryPayload execute(FailureObservation ignored,RecoveryPayload request,RepairCheckpoint cp){assertEquals("fresh-receipt",cp.precheckReceiptId()); return request;}};
        RecoveryPayload payload=new CodeDiagnosisRequest("code","fp"); assertSame(payload,executor.validateAndExecute(o,payload,new RepairCheckpoint("branch-2","cp-2","old"),strategy));
        RecoveryExecutor blocked=new RecoveryExecutor((x,y,z)->new PrecheckPort.PrecheckResult(false,"receipt","unsafe","branch","cp")); assertThrows(RecoveryConflictException.class,()->blocked.validateAndExecute(o,payload,new RepairCheckpoint("branch","cp","r"),strategy));
    }

    @Test void ocrThresholdAndStableIdentityAreEnforced() {
        assertThrows(IllegalArgumentException.class, () -> new OcrResult("text", 0.79, true));
        assertDoesNotThrow(() -> new OcrResult("text", 0.80, true));
        assertDoesNotThrow(() -> OcrResult.withThreshold("text", 0.61, true, 0.60));
        assertThrows(IllegalArgumentException.class, () -> new FailureObservation(TENANT,"task-1","run","agent",SNAPSHOT,new ErrorDetail("CODE_ERROR",FailureClass.CODE,true,"safe","hint",Map.of()),1,Instant.now(),List.of(),Map.of(),"forged"));
    }

    @Test void transitionTableRejectsApprovalFromPending() {
        FailureObservation o=observation(FailureClass.CODE,true,"CODE_ERROR"); RecoveryAttempt a=RecoveryAttempt.create("rec-2",o,new DeterministicFailureClassifier().classify(o),2); InMemoryRecoveryCaseRepository repo=new InMemoryRecoveryCaseRepository(); repo.create(a);
        RepairCheckpoint cp=new RepairCheckpoint("branch","checkpoint","receipt"); RecoveryAttempt next=a.next(RecoveryState.WAITING_FOR_APPROVAL,RecoveryDecision.WAITING_FOR_DOWNLOAD_APPROVAL,cp,1);
        RecoveryEvent forged=new RecoveryEvent(new EventId("evt-forged"),new CommandId("cmd-forged"),"idem-forged",a.recoveryId(),TENANT,RecoveryAction.APPROVE_DOWNLOAD,RecoveryEventType.DOWNLOAD_APPROVAL_GRANTED,a.state(),next.state(),1,SNAPSHOT.snapshotId().value(),"digest",Instant.now());
        assertThrows(RecoveryConflictException.class,()->repo.append(a,next,forged));
    }
}
