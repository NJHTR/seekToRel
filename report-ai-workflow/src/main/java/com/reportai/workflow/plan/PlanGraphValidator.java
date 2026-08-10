package com.reportai.workflow.plan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Deterministic, bounded validator for persisted plan DAGs. */
public final class PlanGraphValidator {
    public PlanGraphValidator() {
    }

    /** Validates a definition and returns its deterministic topological order. */
    public static List<String> validate(PlanDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        List<String> violations = new ArrayList<>();
        List<PlanNode> nodes = definition.nodes();
        List<PlanEdge> edges = definition.edges();
        if (nodes.isEmpty()) {
            violations.add("plan must contain at least one node");
            throw new PlanGraphValidationException(violations);
        }
        if (nodes.size() > PlanRules.MAX_NODES) violations.add("plan has too many nodes");
        if (edges.size() > PlanRules.MAX_EDGES) violations.add("plan has too many edges");

        Map<String, PlanNode> byId = new TreeMap<>();
        for (PlanNode node : nodes) {
            if (byId.putIfAbsent(node.nodeId(), node) != null) {
                violations.add("duplicate node: " + node.nodeId());
            }
        }
        Map<String, List<PlanEdge>> outgoing = new TreeMap<>();
        Map<String, List<PlanEdge>> incoming = new TreeMap<>();
        Set<String> edgeKeys = new HashSet<>();
        for (PlanEdge edge : edges) {
            String key = edge.fromNodeId() + "\u0000" + edge.toNodeId() + "\u0000"
                    + edge.semantics() + "\u0000" + Objects.toString(edge.reference(), "");
            if (!edgeKeys.add(key)) violations.add("duplicate edge: " + key);
            if (edge.fromNodeId().equals(edge.toNodeId())) violations.add("self-loop: " + edge.fromNodeId());
            if (!byId.containsKey(edge.fromNodeId())) violations.add("edge source is unknown: " + edge.fromNodeId());
            if (!byId.containsKey(edge.toNodeId())) violations.add("edge target is unknown: " + edge.toNodeId());
            outgoing.computeIfAbsent(edge.fromNodeId(), ignored -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.toNodeId(), ignored -> new ArrayList<>()).add(edge);
            if (edge.semantics() == PlanEdgeSemantics.CONDITIONAL
                    && !definition.allowsCondition(edge.conditionRef())) {
                violations.add("condition is not allow-listed: " + edge.conditionRef());
            }
        }
        outgoing.forEach((node, list) -> {
            if (list.size() > PlanRules.MAX_OUT_DEGREE) {
                violations.add("out-degree exceeds bound for node: " + node);
            }
            long parallel = list.stream().filter(edge -> edge.semantics() == PlanEdgeSemantics.PARALLEL).count();
            if (parallel == 1) violations.add("parallel fan-out must contain at least two edges: " + node);
        });

        List<PlanNode> starts = nodes.stream().filter(node -> node.kind() == PlanNodeKind.START).toList();
        List<PlanNode> ends = nodes.stream().filter(node -> node.kind() == PlanNodeKind.END).toList();
        if (starts.size() != 1) violations.add("plan must contain exactly one START node");
        if (ends.size() != 1) violations.add("plan must contain exactly one END node");
        if (starts.size() == 1 && incoming.getOrDefault(starts.getFirst().nodeId(), List.of()).size() != 0) {
            violations.add("START node must not have incoming edges");
        }
        if (ends.size() == 1 && !outgoing.getOrDefault(ends.getFirst().nodeId(), List.of()).isEmpty()) {
            violations.add("END node must not have outgoing edges");
        }

        for (PlanEdge edge : edges) {
            PlanNode target = byId.get(edge.toNodeId());
            PlanNode source = byId.get(edge.fromNodeId());
            if (edge.semantics() == PlanEdgeSemantics.JOIN) {
                PlanNode reference = byId.get(edge.joinRef());
                if (reference == null || reference.kind() != PlanNodeKind.JOIN) {
                    violations.add("JOIN edge references a non-JOIN node: " + edge.joinRef());
                }
                if (target != null && target.kind() != PlanNodeKind.JOIN) {
                    violations.add("JOIN edge target must be a JOIN node: " + edge.toNodeId());
                }
            }
            if (source != null && source.kind() == PlanNodeKind.START
                    && edge.semantics() == PlanEdgeSemantics.JOIN) {
                violations.add("START node cannot emit a JOIN edge");
            }
        }
        for (PlanNode node : nodes) {
            if (node.kind() == PlanNodeKind.JOIN) {
                List<PlanEdge> joins = incoming.getOrDefault(node.nodeId(), List.of()).stream()
                        .filter(edge -> edge.semantics() == PlanEdgeSemantics.JOIN).toList();
                if (joins.size() < 2) violations.add("JOIN node requires at least two JOIN inputs: " + node.nodeId());
            }
        }
        if (!violations.isEmpty()) throw new PlanGraphValidationException(violations);

        List<String> order = topologicalOrder(byId.keySet(), outgoing, incoming);
        if (starts.size() == 1 && ends.size() == 1) {
            Set<String> reachable = traverse(starts.getFirst().nodeId(), outgoing, true);
            Set<String> canReachEnd = traverse(ends.getFirst().nodeId(), incoming, false);
            if (reachable.size() != byId.size()) {
                violations.add("plan contains nodes unreachable from START");
            }
            if (canReachEnd.size() != byId.size()) {
                violations.add("plan contains nodes that cannot reach END");
            }
            if (!violations.isEmpty()) throw new PlanGraphValidationException(violations);
        }
        return List.copyOf(order);
    }

    public static List<String> deterministicTopologicalOrder(PlanDefinition definition) {
        return validate(definition);
    }

    public static List<String> topologicalOrder(PlanDefinition definition) {
        return validate(definition);
    }

    public static List<String> validateAndOrder(PlanDefinition definition) {
        return validate(definition);
    }

    public static void assertValid(PlanDefinition definition) {
        validate(definition);
    }

    private static List<String> topologicalOrder(Set<String> ids,
                                                  Map<String, List<PlanEdge>> outgoing,
                                                  Map<String, List<PlanEdge>> incoming) {
        Map<String, Integer> inDegree = new HashMap<>();
        ids.forEach(id -> inDegree.put(id, incoming.getOrDefault(id, List.of()).size()));
        PriorityQueue<String> ready = new PriorityQueue<>();
        inDegree.forEach((id, degree) -> { if (degree == 0) ready.add(id); });
        List<String> order = new ArrayList<>(ids.size());
        while (!ready.isEmpty()) {
            String id = ready.remove();
            order.add(id);
            outgoing.getOrDefault(id, List.of()).stream()
                    .sorted(Comparator.comparing(PlanEdge::toNodeId)
                            .thenComparing(edge -> edge.semantics().name()))
                    .forEach(edge -> {
                        int remaining = inDegree.merge(edge.toNodeId(), -1, Integer::sum);
                        if (remaining == 0) ready.add(edge.toNodeId());
                    });
        }
        if (order.size() != ids.size()) {
            throw new PlanGraphValidationException(List.of("plan contains a cycle"));
        }
        return order;
    }

    private static Set<String> traverse(String start, Map<String, List<PlanEdge>> adjacency, boolean forward) {
        Set<String> seen = new TreeSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!seen.add(current)) continue;
            adjacency.getOrDefault(current, List.of()).forEach(edge ->
                    queue.addLast(forward ? edge.toNodeId() : edge.fromNodeId()));
        }
        return seen;
    }
}
