package com.reportai.workflow.plan;

import com.reportai.common.contract.CanonicalJson;
import com.reportai.common.contract.Sha256Digest;
import com.reportai.common.tenant.TenantId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Versioned, tenant-scoped immutable graph definition. */
public record PlanDefinition(String planId, TenantId tenantId, long version,
                             List<PlanNode> nodes, List<PlanEdge> edges,
                             Set<String> conditionAllowList) {
    public PlanDefinition {
        planId = PlanRules.id(planId, "planId");
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        if (version <= 0) {
            throw new IllegalArgumentException("plan version must be positive");
        }
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        if (nodes.size() > PlanRules.MAX_NODES) {
            throw new IllegalArgumentException("plan has too many nodes");
        }
        if (edges.size() > PlanRules.MAX_EDGES) {
            throw new IllegalArgumentException("plan has too many edges");
        }
        if (nodes.stream().anyMatch(Objects::isNull) || edges.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("plan nodes and edges must not contain null");
        }
        if (nodes.stream().map(PlanNode::nodeId).distinct().count() != nodes.size()) {
            throw new IllegalArgumentException("plan node IDs must be unique");
        }
        conditionAllowList = boundedConditions(conditionAllowList, edges);
    }

    public PlanDefinition(String planId, TenantId tenantId, long version,
                           List<PlanNode> nodes, List<PlanEdge> edges) {
        this(planId, tenantId, version, nodes, edges, Set.of());
    }

    /** Convenience constructor for callers that use the version before tenant scope. */
    public PlanDefinition(TenantId tenantId, String planId, long version,
                           List<PlanNode> nodes, List<PlanEdge> edges) {
        this(planId, tenantId, version, nodes, edges);
    }

    public PlanDefinition(String planId, TenantId tenantId, int version,
                           List<PlanNode> nodes, List<PlanEdge> edges) {
        this(planId, tenantId, (long) version, nodes, edges);
    }

    public List<String> nodeIds() {
        return nodes.stream().map(PlanNode::nodeId).toList();
    }

    public Map<String, PlanNode> nodesById() {
        Map<String, PlanNode> result = new LinkedHashMap<>();
        nodes.stream().sorted(Comparator.comparing(PlanNode::nodeId))
                .forEach(node -> result.put(node.nodeId(), node));
        return Collections.unmodifiableMap(result);
    }

    public boolean allowsCondition(String conditionRef) {
        return conditionRef != null && conditionAllowList.contains(conditionRef);
    }

    public String startNodeId() {
        return nodes.stream().filter(node -> node.kind() == PlanNodeKind.START)
                .map(PlanNode::nodeId).findFirst().orElse(null);
    }

    public String endNodeId() {
        return nodes.stream().filter(node -> node.kind() == PlanNodeKind.END)
                .map(PlanNode::nodeId).findFirst().orElse(null);
    }

    public Sha256Digest digest() {
        List<Map<String, Object>> nodeValues = nodes.stream()
                .sorted(Comparator.comparing(PlanNode::nodeId))
                .map(node -> Map.<String, Object>of(
                        "id", node.nodeId(), "kind", node.kind().name(),
                        "operation", node.operation(), "metadata", node.metadata()))
                .toList();
        List<Map<String, Object>> edgeValues = edges.stream()
                .sorted(Comparator.comparing(PlanEdge::fromNodeId)
                        .thenComparing(PlanEdge::toNodeId)
                        .thenComparing(edge -> edge.semantics().name())
                        .thenComparing(edge -> Objects.toString(edge.reference(), "")))
                .map(edge -> Map.<String, Object>of(
                        "from", edge.fromNodeId(), "to", edge.toNodeId(),
                        "semantics", edge.semantics().name(),
                        "reference", Objects.toString(edge.reference(), "")))
                .toList();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("planId", planId);
        value.put("tenantId", tenantId.value());
        value.put("version", version);
        value.put("nodes", nodeValues);
        value.put("edges", edgeValues);
        value.put("conditions", new ArrayList<>(conditionAllowList));
        return Sha256Digest.ofText(CanonicalJson.encode(value));
    }

    private static Set<String> boundedConditions(Set<String> source, List<PlanEdge> edges) {
        TreeSet<String> conditions = new TreeSet<>();
        if (source != null) {
            source.forEach(condition -> conditions.add(PlanRules.condition(condition)));
        }
        if (conditions.size() > PlanRules.MAX_CONDITIONS) {
            throw new IllegalArgumentException("plan has too many condition references");
        }
        if (edges != null) {
            edges.stream().filter(edge -> edge != null && edge.semantics() == PlanEdgeSemantics.CONDITIONAL)
                    .map(PlanEdge::conditionRef).forEach(condition -> {
                        if (!conditions.contains(condition)) {
                            throw new IllegalArgumentException("conditional edge references an undeclared condition");
                        }
                    });
        }
        return Collections.unmodifiableSet(conditions);
    }
}
