package com.reportai.scheduler.plan;

import com.reportai.common.contract.CommandId;
import com.reportai.common.contract.InputSnapshot;
import com.reportai.common.contract.Sha256Digest;
import com.reportai.common.contract.SnapshotId;
import com.reportai.common.contract.TaskId;
import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlanNodeSchedulerStoreTest {
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");
    private static final TenantId TENANT_A = new TenantId("tenant-a");

    @Test
    void rejectsMissingExpiredOrDeniedValidation() {
        InMemoryPlanNodeSchedulerStore store = new InMemoryPlanNodeSchedulerStore();
        PlanNodeSubmission expired = submission("node-expired", TENANT_A, 0, 0,
                new PrecheckAttestation("att-expired", TENANT_A, digest(), true,
                        NOW.minusSeconds(20), NOW.minusSeconds(1)));
        assertThrows(PlanNodeValidationException.class, () -> store.submit(expired, NOW));

        PlanNodeSubmission denied = submission("node-denied", TENANT_A, 0, 0,
                new PrecheckAttestation("att-denied", TENANT_A, digest(), false,
                        NOW.minusSeconds(1), NOW.plusSeconds(60)));
        assertThrows(PlanNodeValidationException.class, () -> store.submit(denied, NOW));
    }

    @Test
    void ordersByPriorityThenDurableFairnessFields() {
        InMemoryPlanNodeSchedulerStore store = new InMemoryPlanNodeSchedulerStore(
                new ResourceCapacity(1000, 1000, 1000, 3));
        store.submit(submission("low", TENANT_A, 0, 2), NOW);
        store.submit(submission("high-late", TENANT_A, 20, 9), NOW);
        store.submit(submission("high-early", new TenantId("tenant-b"), 20, 8), NOW);
        assertEquals("high-early", store.claimNext("worker-1", NOW, Duration.ofMinutes(5)).orElseThrow()
                .ref().nodeId());
        assertEquals("high-late", store.claimNext("worker-2", NOW, Duration.ofMinutes(5)).orElseThrow()
                .ref().nodeId());
    }

    @Test
    void preventsResourceOversubscription() {
        InMemoryPlanNodeSchedulerStore store = new InMemoryPlanNodeSchedulerStore(
                new ResourceCapacity(100, 100, 100, 1));
        store.submit(submission("first", TENANT_A, 1, 0), NOW);
        store.submit(submission("second", TENANT_A, 1, 1), NOW);
        PlanNode first = store.claimNext("worker-1", NOW, Duration.ofMinutes(5)).orElseThrow();
        assertEquals(1, store.reservedResources().slots());
        assertTrue(store.claim(first.ref(), "worker-2", first.version(), NOW, Duration.ofMinutes(5)).isEmpty());
        assertTrue(store.claimNext("worker-2", NOW, Duration.ofMinutes(5)).isEmpty());
    }

    @Test
    void leaseExpiryAndCasProtectExclusiveOwnership() {
        InMemoryPlanNodeSchedulerStore store = new InMemoryPlanNodeSchedulerStore(
                new ResourceCapacity(1000, 1000, 1000, 3));
        PlanNode ready = store.submit(submission("lease", TENANT_A, 1, 0), NOW).node();
        PlanNode claimed = store.claim(ready.ref(), "worker-1", ready.version(), NOW,
                Duration.ofSeconds(10)).orElseThrow();
        assertTrue(store.claim(ready.ref(), "worker-2", ready.version(), NOW, Duration.ofSeconds(10)).isEmpty());
        assertFalse(store.renewLease(ready.ref(), "worker-1", ready.version(), NOW.plusSeconds(1), Duration.ofSeconds(10)));
        assertTrue(store.renewLease(ready.ref(), "worker-1", claimed.version(), NOW.plusSeconds(1), Duration.ofSeconds(10)));
        assertEquals(1, store.releaseExpiredLeases(NOW.plusSeconds(20)));
        PlanNode reclaimed = store.claim(ready.ref(), "worker-2", claimed.version() + 2, NOW.plusSeconds(20),
                Duration.ofSeconds(10)).orElseThrow();
        assertEquals("worker-2", reclaimed.lease().owner());
    }

    @Test
    void idempotencyDuplicateReturnsExistingAndDigestConflictFails() {
        InMemoryPlanNodeSchedulerStore store = new InMemoryPlanNodeSchedulerStore();
        PlanNodeSubmission first = submission("idem", TENANT_A, 1, 0);
        assertTrue(store.submit(first, NOW).created());
        assertFalse(store.submit(first, NOW.plusSeconds(1)).created());
        PlanNodeSubmission conflicting = new PlanNodeSubmission(first.ref(), first.inputSnapshot(), first.attestation(),
                new Priority(2), first.fairnessKey(), first.resourceRequest(), first.idempotencyKey());
        assertThrows(PlanNodeIdempotencyConflictException.class, () -> store.submit(conflicting, NOW));
    }

    @Test
    void cancellationIsRequestThenAckAndSurvivesRehydration() {
        InMemoryPlanNodeSchedulerStore firstStore = new InMemoryPlanNodeSchedulerStore();
        PlanNode ready = firstStore.submit(submission("cancel", TENANT_A, 1, 0), NOW).node();
        PlanNode claimed = firstStore.claim(ready.ref(), "worker-1", ready.version(), NOW,
                Duration.ofMinutes(5)).orElseThrow();
        PlanNodeCancelCommand command = new PlanNodeCancelCommand(new CommandId("cancel-cmd"), "cancel-key",
                claimed.ref(), TENANT_A, claimed.version(), "user requested", NOW);
        PlanNodeCancelResult requested = firstStore.requestCancellation(command);
        assertTrue(requested.requested());
        assertEquals(PlanNodeStatus.CANCELLATION_REQUESTED, requested.node().status());
        InMemoryPlanNodeSchedulerStore restarted = new InMemoryPlanNodeSchedulerStore(ResourceCapacity.defaults(),
                firstStore.snapshot());
        PlanNode cancelled = restarted.acknowledgeCancellation(claimed.ref(), "worker-1",
                requested.node().version(), NOW.plusSeconds(1)).orElseThrow();
        assertEquals(PlanNodeStatus.CANCELLED, cancelled.status());
        assertEquals(0, restarted.reservedResources().slots());
        assertTrue(restarted.requestCancellation(command).duplicate());
    }

    @Test
    void runningAndTerminalTransitionsUseLeaseCasAndReleaseResources() {
        InMemoryPlanNodeSchedulerStore store = new InMemoryPlanNodeSchedulerStore();
        PlanNode ready = store.submit(submission("lifecycle", TENANT_A, 1, 0), NOW).node();
        PlanNode claimed = store.claim(ready.ref(), "worker-1", ready.version(), NOW,
                Duration.ofMinutes(5)).orElseThrow();
        assertTrue(store.markRunning(ready.ref(), "worker-1", claimed.version(), NOW.plusSeconds(1)));
        assertFalse(store.markSucceeded(ready.ref(), "worker-1", claimed.version(), NOW.plusSeconds(2)));
        PlanNode running = store.find(ready.ref(), TENANT_A).orElseThrow();
        assertTrue(store.markSucceeded(ready.ref(), "worker-1", running.version(), NOW.plusSeconds(2)));
        assertEquals(PlanNodeStatus.SUCCEEDED, store.find(ready.ref(), TENANT_A).orElseThrow().status());
        assertEquals(0, store.reservedResources().slots());
    }

    @Test
    void tenantIsolationAppliesToLookupAndCancellation() {
        InMemoryPlanNodeSchedulerStore store = new InMemoryPlanNodeSchedulerStore();
        PlanNode node = store.submit(submission("tenant", TENANT_A, 1, 0), NOW).node();
        assertTrue(store.find(node.ref(), new TenantId("tenant-b")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new PlanNodeCancelCommand(new CommandId("cancel-other"),
                "key-other", node.ref(), new TenantId("tenant-b"), node.version(), "reason", NOW));
    }

    private static PlanNodeSubmission submission(String nodeId, TenantId tenant, int priority, long tenantSequence) {
        return submission(nodeId, tenant, priority, tenantSequence,
                new PrecheckAttestation("att-" + nodeId, tenant, digest(), true,
                        NOW.minusSeconds(1), NOW.plusSeconds(3600)));
    }

    private static PlanNodeSubmission submission(String nodeId, TenantId tenant, int priority,
                                                 long tenantSequence, PrecheckAttestation attestation) {
        InputSnapshot snapshot = new InputSnapshot(1, new SnapshotId("snapshot-" + nodeId), tenant,
                List.of(), digest(), List.of(), NOW, Map.of("node", nodeId));
        PlanNodeRef ref = new PlanNodeRef(tenant, new TaskId("task-" + nodeId), "plan-1", 1, nodeId);
        return new PlanNodeSubmission(ref, snapshot, attestation, new Priority(priority),
                new FairnessKey(tenantSequence, tenantSequence, NOW.plusMillis(tenantSequence)),
                new ResourceRequest(10, 10, 10, 1), "idem-" + nodeId);
    }

    private static Sha256Digest digest() {
        return Sha256Digest.ofText("payload");
    }
}
