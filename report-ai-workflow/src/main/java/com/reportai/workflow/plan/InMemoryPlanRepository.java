package com.reportai.workflow.plan;

import com.reportai.common.tenant.TenantId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Deterministic adapter that mirrors the CAS/idempotency requirements of a durable store. */
public final class InMemoryPlanRepository implements PlanRepository {
    private final Map<String, PlanDefinition> definitions = new HashMap<>();
    private final Map<String, PlanDefinition> latestDefinitions = new HashMap<>();
    private final Map<String, PlanDefinition> definitionCommands = new HashMap<>();
    private final Map<String, PlanRun> runs = new HashMap<>();
    private final Map<String, List<PlanRunEvent>> runLogs = new HashMap<>();
    private final Map<String, PlanRunEvent> runCommands = new HashMap<>();

    @Override
    public synchronized PlanDefinition create(PlanDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        PlanGraphValidator.assertValid(definition);
        String key = definitionKey(definition.tenantId(), definition.planId(), definition.version());
        if (definitions.putIfAbsent(key, definition) != null) {
            throw new PlanConflictException("plan version already exists");
        }
        latestDefinitions.merge(latestKey(definition.tenantId(), definition.planId()), definition,
                (oldValue, newValue) -> oldValue.version() >= newValue.version() ? oldValue : newValue);
        return definition;
    }

    @Override
    public synchronized PlanDefinition create(PlanDefinition definition, String idempotencyKey) {
        Objects.requireNonNull(definition, "definition");
        String key = commandKey(definition.tenantId(), definition.planId(), idempotencyKey);
        PlanDefinition prior = definitionCommands.get(key);
        if (prior != null) {
            if (!prior.digest().equals(definition.digest())) {
                throw new PlanConflictException("idempotency key reused with a different plan");
            }
            return prior;
        }
        PlanDefinition created = create(definition);
        definitionCommands.put(key, created);
        return created;
    }

    @Override
    public synchronized Optional<PlanDefinition> findDefinition(TenantId tenantId, String planId, long version) {
        Objects.requireNonNull(tenantId, "tenantId");
        return Optional.ofNullable(definitions.get(definitionKey(tenantId, PlanRules.id(planId, "planId"), version)));
    }

    @Override
    public synchronized Optional<PlanDefinition> latestDefinition(TenantId tenantId, String planId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return Optional.ofNullable(latestDefinitions.get(latestKey(tenantId, PlanRules.id(planId, "planId"))));
    }

    @Override
    public synchronized DefinitionWriteResult appendDefinition(PlanDefinition expected,
                                                                 PlanDefinition next,
                                                                 String idempotencyKey) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(next, "next");
        String commandKey = commandKey(expected.tenantId(), expected.planId(), idempotencyKey);
        PlanDefinition duplicate = definitionCommands.get(commandKey);
        if (duplicate != null) {
            if (!duplicate.digest().equals(next.digest())) {
                throw new PlanConflictException("idempotency key reused with a different plan");
            }
            return new DefinitionWriteResult(duplicate, true);
        }
        PlanDefinition current = latestDefinitions.get(latestKey(expected.tenantId(), expected.planId()));
        if (current == null) throw new PlanConflictException("plan definition not found");
        if (!current.equals(expected)) throw new PlanConflictException("stale plan definition version");
        if (!next.tenantId().equals(expected.tenantId()) || !next.planId().equals(expected.planId())
                || next.version() != expected.version() + 1) {
            throw new PlanConflictException("plan identity or version sequence is invalid");
        }
        PlanGraphValidator.assertValid(next);
        String nextKey = definitionKey(next.tenantId(), next.planId(), next.version());
        if (definitions.putIfAbsent(nextKey, next) != null) {
            throw new PlanConflictException("plan version already exists");
        }
        latestDefinitions.put(latestKey(next.tenantId(), next.planId()), next);
        definitionCommands.put(commandKey, next);
        return new DefinitionWriteResult(next, false);
    }

    @Override
    public synchronized PlanRun createRun(PlanRun run) {
        Objects.requireNonNull(run, "run");
        PlanDefinition definition = definitions.get(
                definitionKey(run.tenantId(), run.planId(), run.planVersion()));
        if (definition == null || !definition.digest().equals(run.planDigest())) {
            throw new PlanConflictException("run references an unknown plan version or digest");
        }
        PlanGraphValidator.assertValid(definition);
        if (!run.nodeStates().keySet().equals(definition.nodes().stream()
                .map(PlanNode::nodeId).collect(java.util.stream.Collectors.toSet()))) {
            throw new PlanConflictException("run node state set does not match plan graph");
        }
        String key = runKey(run.tenantId(), run.runId());
        if (runs.putIfAbsent(key, run) != null) throw new PlanConflictException("run already exists");
        runLogs.put(key, new ArrayList<>());
        return run;
    }

    @Override
    public synchronized Optional<PlanRun> findRun(TenantId tenantId, String runId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return Optional.ofNullable(runs.get(runKey(tenantId, PlanRules.id(runId, "runId"))));
    }

    @Override
    public synchronized RunWriteResult appendRun(PlanRun expected, PlanRun next, PlanRunEvent event) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(event, "event");
        String key = runKey(expected.tenantId(), expected.runId());
        String commandKey = commandKey(expected.tenantId(), expected.runId(), event.idempotencyKey());
        PlanRunEvent duplicate = runCommands.get(commandKey);
        if (duplicate != null) {
            if (!duplicate.sameCommand(event)) {
                throw new PlanConflictException("idempotency key reused with a different run command");
            }
            return new RunWriteResult(runs.get(key), duplicate, true);
        }
        PlanRun current = runs.get(key);
        if (current == null) throw new PlanConflictException("run not found");
        if (!current.equals(expected)) throw new PlanConflictException("stale plan run version");
        if (!next.sameIdentity(expected) || next.version() != expected.version() + 1
                || next.updatedAt().isBefore(expected.updatedAt())) {
            throw new PlanConflictException("run identity or version sequence is invalid");
        }
        if (!next.nodeStates().keySet().equals(expected.nodeStates().keySet())) {
            throw new PlanConflictException("run node set cannot change");
        }
        if (!event.runId().equals(next.runId()) || !event.tenantId().equals(next.tenantId())
                || !event.planId().equals(next.planId()) || event.planVersion() != next.planVersion()
                || event.from() != expected.state() || event.to() != next.state()
                || event.version() != next.version()
                || !event.nodeStateDigest().equals(next.nodeStateDigest())) {
            throw new PlanConflictException("run event does not match transition");
        }
        if (!validTransition(expected.state(), next.state())) {
            throw new PlanConflictException("invalid plan run transition");
        }
        runs.put(key, next);
        runLogs.computeIfAbsent(key, ignored -> new ArrayList<>()).add(event);
        runCommands.put(commandKey, event);
        return new RunWriteResult(next, event, false);
    }

    @Override
    public synchronized List<PlanRunEvent> runEvents(TenantId tenantId, String runId) {
        List<PlanRunEvent> events = runLogs.get(runKey(tenantId, PlanRules.id(runId, "runId")));
        return events == null ? List.of() : List.copyOf(events);
    }

    @Override
    public synchronized PlanRun replayRun(TenantId tenantId, String runId) {
        String key = runKey(tenantId, PlanRules.id(runId, "runId"));
        PlanRun current = runs.get(key);
        if (current == null) throw new PlanConflictException("run not found");
        PlanRunState state = PlanRunState.PENDING;
        long expectedVersion = 1;
        String digest = current.nodeStateDigest();
        for (PlanRunEvent event : runLogs.getOrDefault(key, List.of())) {
            if (!event.tenantId().equals(tenantId) || !event.runId().equals(current.runId())
                    || !event.planId().equals(current.planId()) || event.planVersion() != current.planVersion()
                    || event.version() != expectedVersion || event.from() != state
                    || !validTransition(event.from(), event.to())) {
                throw new PlanConflictException("tampered plan run event log");
            }
            state = event.to();
            digest = event.nodeStateDigest();
            expectedVersion++;
        }
        if (current.version() != expectedVersion - 1 || current.state() != state
                || !current.nodeStateDigest().equals(digest)) {
            throw new PlanConflictException("plan run snapshot does not match event replay");
        }
        return current;
    }

    private static boolean validTransition(PlanRunState from, PlanRunState to) {
        if (from == to) return false;
        return switch (from) {
            case PENDING -> to == PlanRunState.RUNNING || to == PlanRunState.CANCELLED;
            case RUNNING -> to == PlanRunState.WAITING || to == PlanRunState.COMPLETED
                    || to == PlanRunState.FAILED || to == PlanRunState.CANCELLED || to == PlanRunState.PAUSED;
            case WAITING -> to == PlanRunState.RUNNING || to == PlanRunState.FAILED || to == PlanRunState.CANCELLED;
            case PAUSED -> to == PlanRunState.RUNNING || to == PlanRunState.CANCELLED;
            case COMPLETED, FAILED, CANCELLED -> false;
        };
    }

    private static String definitionKey(TenantId tenantId, String planId, long version) {
        return tenantId.value() + "\u0000" + planId + "\u0000" + version;
    }

    private static String latestKey(TenantId tenantId, String planId) {
        return tenantId.value() + "\u0000" + planId;
    }

    private static String runKey(TenantId tenantId, String runId) {
        return tenantId.value() + "\u0000" + runId;
    }

    private static String commandKey(TenantId tenantId, String aggregateId, String idempotencyKey) {
        return tenantId.value() + "\u0000" + aggregateId + "\u0000" + PlanRules.text(idempotencyKey, "idempotencyKey");
    }
}
