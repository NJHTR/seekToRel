package com.reportai.scheduler.plan;

import com.reportai.common.tenant.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic test adapter. Its map represents durable rows; restart tests can export and rehydrate
 * the rows through the collection constructor. Production adapters must preserve the same CAS rules.
 */
public final class InMemoryPlanNodeSchedulerStore implements PlanNodeSchedulerStore {
    private static final Duration MAX_LEASE = Duration.ofDays(1);
    private final ResourceCapacity capacity;
    private final Map<String, PlanNode> nodes = new ConcurrentHashMap<>();

    public InMemoryPlanNodeSchedulerStore() {
        this(ResourceCapacity.defaults());
    }

    public InMemoryPlanNodeSchedulerStore(ResourceCapacity capacity) {
        this.capacity = Objects.requireNonNull(capacity, "capacity");
    }

    public InMemoryPlanNodeSchedulerStore(ResourceCapacity capacity, Collection<PlanNode> persisted) {
        this(capacity);
        Objects.requireNonNull(persisted, "persisted").forEach(node -> {
            if (nodes.putIfAbsent(node.ref().durableKey(), node) != null) {
                throw new PlanNodeSchedulerConflictException("duplicate persisted node");
            }
        });
        if (!reservedResources().within(capacity)) {
            throw new PlanNodeSchedulerConflictException("persisted reservations exceed capacity");
        }
    }

    @Override
    public synchronized PlanNodeSubmitResult submit(PlanNodeSubmission submission, Instant now) {
        Objects.requireNonNull(submission, "submission");
        Objects.requireNonNull(now, "now");
        String key = submission.ref().durableKey();
        PlanNode existing = nodes.get(key);
        if (existing != null) {
            if (!existing.requestDigest().equals(submission.requestDigest())) {
                throw new PlanNodeIdempotencyConflictException("node identity has a different request digest");
            }
            return new PlanNodeSubmitResult(existing, false);
        }
        for (PlanNode node : nodes.values()) {
            if (node.ref().tenantId().equals(submission.ref().tenantId())
                    && node.idempotencyKey().equals(submission.idempotencyKey())) {
                if (!node.requestDigest().equals(submission.requestDigest())) {
                    throw new PlanNodeIdempotencyConflictException("idempotency key has a different request digest");
                }
                return new PlanNodeSubmitResult(node, false);
            }
        }
        if (!submission.attestation().isValidFor(submission.inputSnapshot(), submission.ref().tenantId(), now)) {
            throw new PlanNodeValidationException("input snapshot lacks a fresh accepted attestation");
        }
        if (!capacity.includes(submission.resourceRequest())) {
            throw new PlanNodeValidationException("resource request exceeds scheduler capacity");
        }
        PlanNode created = PlanNode.ready(submission, now);
        nodes.put(key, created);
        return new PlanNodeSubmitResult(created, true);
    }

    @Override
    public synchronized Optional<PlanNode> find(PlanNodeRef ref, TenantId tenantId) {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(tenantId, "tenantId");
        return Optional.ofNullable(nodes.get(ref.durableKey())).filter(node -> node.ref().tenantId().equals(tenantId));
    }

    @Override
    public synchronized Optional<PlanNode> claim(PlanNodeRef ref, String owner, long expectedVersion,
                                                  Instant now, Duration leaseDuration) {
        Objects.requireNonNull(ref, "ref");
        validateOwnerAndLease(owner, now, leaseDuration);
        PlanNode current = nodes.get(ref.durableKey());
        if (current == null || current.version() != expectedVersion || !current.claimableAt(now)) {
            return Optional.empty();
        }
        if (!current.attestation().isValidFor(current.inputSnapshot(), ref.tenantId(), now)) {
            return Optional.empty();
        }
        if (!reservedResources().canAdd(current.resourceRequest(), capacity)) {
            return Optional.empty();
        }
        return Optional.of(claim(current, owner, now, leaseDuration));
    }

    @Override
    public synchronized Optional<PlanNode> claimNext(String owner, Instant now, Duration leaseDuration) {
        validateOwnerAndLease(owner, now, leaseDuration);
        Comparator<PlanNode> order = Comparator
                .comparing((PlanNode node) -> node.priority().value(), Comparator.reverseOrder())
                .thenComparing(node -> node.fairnessKey().tenantReadySequence())
                .thenComparing(node -> node.fairnessKey().globalReadySequence())
                .thenComparing(node -> node.fairnessKey().enqueuedAt())
                .thenComparing(node -> node.ref().durableKey());
        ResourceUsage used = reservedResources();
        return nodes.values().stream().filter(node -> node.claimableAt(now))
                .sorted(order)
                .filter(node -> node.attestation().isValidFor(node.inputSnapshot(), node.ref().tenantId(), now))
                .filter(node -> used.canAdd(node.resourceRequest(), capacity))
                .findFirst()
                .map(node -> claim(node, owner, now, leaseDuration));
    }

    @Override
    public synchronized boolean renewLease(PlanNodeRef ref, String owner, long expectedVersion,
                                            Instant now, Duration leaseDuration) {
        validateOwnerAndLease(owner, now, leaseDuration);
        PlanNode current = nodes.get(ref.durableKey());
        if (!owned(current, owner, expectedVersion, now)) return false;
        PlanNodeLease renewed = new PlanNodeLease(owner, current.lease().token(), now, now.plus(leaseDuration));
        PlanNode next = copy(current, current.status(), renewed, current.reservation(),
                null, null, current.version() + 1, now);
        nodes.put(ref.durableKey(), next);
        return true;
    }

    @Override
    public synchronized boolean releaseLease(PlanNodeRef ref, String owner, long expectedVersion, Instant now) {
        Objects.requireNonNull(now, "now");
        PlanNode current = nodes.get(ref.durableKey());
        if (!owned(current, owner, expectedVersion, now)) return false;
        PlanNode next = copy(current, PlanNodeStatus.READY, null, null,
                current.cancellationIdempotencyKey(), current.cancellationRequestDigest(), current.version() + 1, now);
        nodes.put(ref.durableKey(), next);
        return true;
    }

    @Override
    public synchronized boolean markRunning(PlanNodeRef ref, String owner, long expectedVersion, Instant now) {
        Objects.requireNonNull(now, "now");
        PlanNode current = nodes.get(ref.durableKey());
        if (!owned(current, owner, expectedVersion, now) || current.status() != PlanNodeStatus.CLAIMED) return false;
        nodes.put(ref.durableKey(), copy(current, PlanNodeStatus.RUNNING, current.lease(), current.reservation(),
                current.cancellationIdempotencyKey(), current.cancellationRequestDigest(), current.version() + 1, now));
        return true;
    }

    @Override
    public synchronized boolean markSucceeded(PlanNodeRef ref, String owner, long expectedVersion, Instant now) {
        return finish(ref, owner, expectedVersion, now, PlanNodeStatus.SUCCEEDED);
    }

    @Override
    public synchronized boolean markFailed(PlanNodeRef ref, String owner, long expectedVersion, Instant now) {
        return finish(ref, owner, expectedVersion, now, PlanNodeStatus.FAILED);
    }

    private boolean finish(PlanNodeRef ref, String owner, long expectedVersion, Instant now, PlanNodeStatus status) {
        Objects.requireNonNull(now, "now");
        PlanNode current = nodes.get(ref.durableKey());
        if (!owned(current, owner, expectedVersion, now)) return false;
        nodes.put(ref.durableKey(), copy(current, status, null, null,
                current.cancellationIdempotencyKey(), current.cancellationRequestDigest(), current.version() + 1, now));
        return true;
    }

    @Override
    public synchronized int releaseExpiredLeases(Instant now) {
        Objects.requireNonNull(now, "now");
        int released = 0;
        for (PlanNode current : new ArrayList<>(nodes.values())) {
            if ((current.status() == PlanNodeStatus.CLAIMED || current.status() == PlanNodeStatus.RUNNING
                    || current.status() == PlanNodeStatus.CANCELLATION_REQUESTED)
                    && current.lease() != null && !current.lease().expiresAt().isAfter(now)) {
                PlanNodeStatus target = current.status() == PlanNodeStatus.CANCELLATION_REQUESTED
                        ? PlanNodeStatus.CANCELLATION_REQUESTED : PlanNodeStatus.READY;
                nodes.put(current.ref().durableKey(), copy(current, target, null, null,
                        current.cancellationIdempotencyKey(), current.cancellationRequestDigest(), current.version() + 1, now));
                released++;
            }
        }
        return released;
    }

    @Override
    public synchronized int reconcileAfterRestart(Instant now) {
        Objects.requireNonNull(now, "now");
        int repaired = releaseExpiredLeases(now);
        for (PlanNode current : new ArrayList<>(nodes.values())) {
            if ((current.status() == PlanNodeStatus.CLAIMED || current.status() == PlanNodeStatus.RUNNING)
                    && (current.lease() == null || current.reservation() == null)) {
                nodes.put(current.ref().durableKey(), copy(current, PlanNodeStatus.FAILED, null, null,
                        current.cancellationIdempotencyKey(), current.cancellationRequestDigest(), current.version() + 1, now));
                repaired++;
            }
        }
        return repaired;
    }

    @Override
    public synchronized PlanNodeCancelResult requestCancellation(PlanNodeCancelCommand command) {
        Objects.requireNonNull(command, "command");
        for (PlanNode node : nodes.values()) {
            if (command.idempotencyKey().equals(node.cancellationIdempotencyKey())) {
                if (!command.requestDigest().equals(node.cancellationRequestDigest())) {
                    throw new PlanNodeIdempotencyConflictException("cancellation idempotency digest conflict");
                }
                return new PlanNodeCancelResult(node, false, true);
            }
        }
        PlanNode current = nodes.get(command.ref().durableKey());
        if (current == null || !current.ref().tenantId().equals(command.tenantId())) {
            throw new PlanNodeSchedulerConflictException("unknown node or tenant");
        }
        if (current.version() != command.expectedVersion()) {
            throw new PlanNodeSchedulerConflictException("stale cancellation version");
        }
        if (current.status() == PlanNodeStatus.CANCELLED || current.status() == PlanNodeStatus.SUCCEEDED
                || current.status() == PlanNodeStatus.FAILED) {
            return new PlanNodeCancelResult(current, false, false);
        }
        PlanNode next = copy(current, PlanNodeStatus.CANCELLATION_REQUESTED, current.lease(), current.reservation(),
                command.idempotencyKey(), command.requestDigest(), current.version() + 1, command.requestedAt());
        nodes.put(current.ref().durableKey(), next);
        return new PlanNodeCancelResult(next, true, false);
    }

    @Override
    public synchronized Optional<PlanNode> acknowledgeCancellation(PlanNodeRef ref, String owner,
                                                                     long expectedVersion, Instant now) {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(now, "now");
        PlanNode current = nodes.get(ref.durableKey());
        if (current == null || current.version() != expectedVersion || current.status() != PlanNodeStatus.CANCELLATION_REQUESTED) {
            return Optional.empty();
        }
        if (current.lease() != null && (!Objects.equals(owner, current.lease().owner()) || !current.lease().activeAt(now))) {
            return Optional.empty();
        }
        PlanNode next = copy(current, PlanNodeStatus.CANCELLED, null, null,
                current.cancellationIdempotencyKey(), current.cancellationRequestDigest(), current.version() + 1, now);
        nodes.put(ref.durableKey(), next);
        return Optional.of(next);
    }

    @Override
    public synchronized ResourceUsage reservedResources() {
        ResourceUsage total = ResourceUsage.zero();
        for (PlanNode node : nodes.values()) {
            if (node.reservation() != null) total = total.plus(node.reservation().request());
        }
        return total;
    }

    @Override
    public synchronized List<PlanNode> snapshot() {
        return List.copyOf(nodes.values());
    }

    private PlanNode claim(PlanNode current, String owner, Instant now, Duration leaseDuration) {
        PlanNodeLease lease = new PlanNodeLease(owner, UUID.randomUUID().toString(), now, now.plus(leaseDuration));
        ResourceReservation reservation = new ResourceReservation(current.resourceRequest(), owner, lease.token(), now);
        PlanNode next = copy(current, PlanNodeStatus.CLAIMED, lease, reservation,
                current.cancellationIdempotencyKey(), current.cancellationRequestDigest(), current.version() + 1, now);
        nodes.put(current.ref().durableKey(), next);
        return next;
    }

    private static boolean owned(PlanNode node, String owner, long expectedVersion, Instant now) {
        return node != null && (node.status() == PlanNodeStatus.CLAIMED || node.status() == PlanNodeStatus.RUNNING)
                && node.lease() != null && node.reservation() != null && node.version() == expectedVersion
                && Objects.equals(owner, node.lease().owner()) && node.lease().activeAt(now);
    }

    private static void validateOwnerAndLease(String owner, Instant now, Duration duration) {
        Objects.requireNonNull(now, "now");
        if (owner == null || owner.isBlank() || owner.length() > 128 || owner.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("owner is invalid");
        }
        if (duration == null || duration.isZero() || duration.isNegative() || duration.compareTo(MAX_LEASE) > 0) {
            throw new IllegalArgumentException("lease duration is outside bounded limits");
        }
    }

    private static PlanNode copy(PlanNode current, PlanNodeStatus status, PlanNodeLease lease,
                                 ResourceReservation reservation, String cancellationKey,
                                 com.reportai.common.contract.Sha256Digest cancellationDigest,
                                 long version, Instant now) {
        return new PlanNode(current.ref(), current.inputSnapshot(), current.attestation(), current.priority(),
                current.fairnessKey(), current.resourceRequest(), current.idempotencyKey(), current.requestDigest(),
                status, lease, reservation, cancellationKey, cancellationDigest, version,
                current.createdAt(), now);
    }
}
