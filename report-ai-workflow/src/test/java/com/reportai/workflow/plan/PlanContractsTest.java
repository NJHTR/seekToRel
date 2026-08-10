package com.reportai.workflow.plan;

import com.reportai.common.contract.TaskId;
import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanContractsTest {
    private static final TenantId TENANT = new TenantId("tenant-a");

    @Test
    void validatesDeterministicParallelJoinOrder() {
        PlanDefinition definition = new PlanDefinition("plan-a", TENANT, 1,
                List.of(PlanNode.start("start"), PlanNode.task("a"), PlanNode.task("b"),
                        PlanNode.join("join"), PlanNode.end("end")),
                List.of(PlanEdge.parallel("start", "a"), PlanEdge.parallel("start", "b"),
                        PlanEdge.join("a", "join"), PlanEdge.join("b", "join"),
                        PlanEdge.serial("join", "end")));

        assertEquals(List.of("start", "a", "b", "join", "end"),
                PlanGraphValidator.deterministicTopologicalOrder(definition));
        assertEquals(definition.digest(), definition.digest());
    }

    @Test
    void rejectsCyclesSelfLoopsAndInvalidJoinReferences() {
        PlanDefinition cycle = new PlanDefinition("cycle", TENANT, 1,
                List.of(PlanNode.start("start"), PlanNode.task("a"), PlanNode.end("end")),
                List.of(PlanEdge.serial("start", "a"), PlanEdge.serial("a", "start"),
                        PlanEdge.serial("a", "end")));
        assertThrows(PlanGraphValidationException.class, () -> PlanGraphValidator.validate(cycle));

        PlanDefinition selfLoop = new PlanDefinition("self", TENANT, 1,
                List.of(PlanNode.start("start"), PlanNode.end("end")),
                List.of(PlanEdge.serial("start", "start"), PlanEdge.serial("start", "end")));
        assertThrows(PlanGraphValidationException.class, () -> PlanGraphValidator.validate(selfLoop));

        PlanDefinition badJoin = new PlanDefinition("join", TENANT, 1,
                List.of(PlanNode.start("start"), PlanNode.task("a"), PlanNode.end("end")),
                List.of(PlanEdge.serial("start", "a"), PlanEdge.join("a", "end", "missing")));
        assertThrows(PlanGraphValidationException.class, () -> PlanGraphValidator.validate(badJoin));

        PlanDefinition splitJoinReference = new PlanDefinition("split-join", TENANT, 1,
                List.of(PlanNode.start("start"), PlanNode.task("a"), PlanNode.join("join-a"),
                        PlanNode.join("join-b"), PlanNode.end("end")),
                List.of(PlanEdge.serial("start", "a"), PlanEdge.join("a", "join-a", "join-b"),
                        PlanEdge.join("a", "join-a"), PlanEdge.serial("join-a", "end")));
        assertThrows(PlanGraphValidationException.class, () -> PlanGraphValidator.validate(splitJoinReference));
    }

    @Test
    void rejectsUnboundedFanoutAndArbitraryConditions() {
        List<PlanNode> nodes = new ArrayList<>();
        List<PlanEdge> edges = new ArrayList<>();
        nodes.add(PlanNode.start("start"));
        nodes.add(PlanNode.end("end"));
        for (int index = 0; index < PlanRules.MAX_OUT_DEGREE + 1; index++) {
            String node = "n" + index;
            nodes.add(PlanNode.task(node));
            edges.add(PlanEdge.serial("start", node));
            edges.add(PlanEdge.serial(node, "end"));
        }
        PlanDefinition fanout = new PlanDefinition("fanout", TENANT, 1, nodes, edges);
        assertThrows(PlanGraphValidationException.class, () -> PlanGraphValidator.validate(fanout));
        assertThrows(IllegalArgumentException.class,
                () -> PlanEdge.conditional("start", "end", "x == 1"));
    }

    @Test
    void conditionalEdgesUseAnExplicitAllowList() {
        PlanDefinition definition = new PlanDefinition("conditional", TENANT, 1,
                List.of(PlanNode.start("start"), PlanNode.conditional("gate", "gate"),
                        PlanNode.task("accepted"), PlanNode.end("end")),
                List.of(PlanEdge.serial("start", "gate"),
                        PlanEdge.conditional("gate", "accepted", "accepted"),
                        PlanEdge.serial("accepted", "end")), Set.of("accepted"));
        assertTrue(definition.allowsCondition("accepted"));
        assertEquals(List.of("start", "gate", "accepted", "end"),
                PlanGraphValidator.validate(definition));
        assertThrows(IllegalArgumentException.class, () -> new PlanDefinition("bad-condition", TENANT, 1,
                definition.nodes(), definition.edges(), Set.of("other")));
        assertThrows(IllegalArgumentException.class, () -> new PlanDefinition("implicit-condition", TENANT, 1,
                definition.nodes(), definition.edges()));
    }

    @Test
    void repositoryEnforcesTenantCasAndIdempotency() {
        InMemoryPlanRepository repository = new InMemoryPlanRepository();
        PlanDefinition first = simple("plan-repo", TENANT, 1);
        repository.create(first, "create-1");
        PlanDefinition second = simple("plan-repo", TENANT, 2);
        PlanRepository.DefinitionWriteResult write = repository.appendDefinition(first, second, "update-1");
        assertEquals(second, write.definition());
        assertTrue(repository.appendDefinition(first, second, "update-1").duplicate());
        assertThrows(PlanConflictException.class,
                () -> repository.appendDefinition(first, simple("plan-repo", TENANT, 2), "update-2"));
        assertTrue(repository.findDefinition(new TenantId("tenant-b"), "plan-repo", 1).isEmpty());
    }

    @Test
    void runSnapshotsAreCasIdempotentAndReplayable() {
        InMemoryPlanRepository repository = new InMemoryPlanRepository();
        PlanDefinition definition = simple("run-plan", TENANT, 1);
        repository.create(definition);
        PlanRun initial = PlanRun.initial("run-1", TENANT, new TaskId("task-1"), definition, Instant.parse("2026-01-01T00:00:00Z"));
        repository.createRun(initial);
        PlanRun running = initial.withState(PlanRunState.RUNNING, Instant.parse("2026-01-01T00:00:01Z"));
        PlanRepository.RunWriteResult write = repository.appendRun(initial, running, "run-start");
        assertEquals(running, write.run());
        assertTrue(repository.appendRun(initial, running, "run-start").duplicate());
        assertEquals(running, repository.replayRun(TENANT, "run-1"));
        assertThrows(PlanConflictException.class,
                () -> repository.appendRun(initial, running.withState(PlanRunState.CANCELLED,
                        Instant.parse("2026-01-01T00:00:02Z")), "stale"));
    }

    @Test
    void bindsRunTenantAndNodeStateSetToPlanIdentity() {
        PlanDefinition definition = simple("identity-plan", TENANT, 1);
        assertThrows(IllegalArgumentException.class,
                () -> PlanRun.initial("wrong-tenant", new TenantId("tenant-b"), "task-1", definition,
                        Instant.parse("2026-01-01T00:00:00Z")));
        InMemoryPlanRepository repository = new InMemoryPlanRepository();
        repository.create(definition);
        PlanRun initial = PlanRun.initial("identity-run", TENANT, "task-1", definition,
                Instant.parse("2026-01-01T00:00:00Z"));
        Map<String, PlanNodeState> hidden = new java.util.HashMap<>(initial.nodeStates());
        hidden.put("hidden", PlanNodeState.PENDING);
        PlanRun tampered = new PlanRun(initial.runId(), initial.tenantId(), initial.taskId(), initial.planId(),
                initial.planVersion(), initial.planDigest(), initial.state(), hidden, initial.version(),
                initial.createdAt(), initial.updatedAt());
        assertThrows(PlanConflictException.class, () -> repository.createRun(tampered));
    }

    private static PlanDefinition simple(String planId, TenantId tenantId, long version) {
        return new PlanDefinition(planId, tenantId, version,
                List.of(PlanNode.start("start"), PlanNode.task("work"), PlanNode.end("end")),
                List.of(PlanEdge.serial("start", "work"), PlanEdge.serial("work", "end")));
    }
}
