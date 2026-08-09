package com.reportai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Final deterministic gate for pipeline state before a release operation. */
public final class CompletenessAgent implements Agent {
    private final String type;
    private final List<String> requiredKeys;

    public CompletenessAgent(List<String> requiredKeys) {
        this("completeness-review", requiredKeys);
    }

    public CompletenessAgent(String type, List<String> requiredKeys) {
        if (type == null || type.isBlank() || type.length() > 128) {
            throw new IllegalArgumentException("type is invalid");
        }
        if (requiredKeys == null || requiredKeys.isEmpty() || requiredKeys.size() > 128
                || requiredKeys.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 128)) {
            throw new IllegalArgumentException("requiredKeys are invalid");
        }
        this.type = type.trim();
        this.requiredKeys = List.copyOf(requiredKeys);
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        if (context == null) {
            return new AgentResult(AgentResult.Status.FAILED, Map.of(), "agent context is required");
        }
        List<String> missing = new ArrayList<>();
        for (String key : requiredKeys) {
            Object value = context.state().get(key);
            if (value == null || (value instanceof String text && text.isBlank())
                    || (value instanceof List<?> values && values.isEmpty())) {
                missing.add(key);
            }
        }
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("reviewPassed", missing.isEmpty());
        outputs.put("missingStateKeys", List.copyOf(missing));
        if (!missing.isEmpty()) {
            return new AgentResult(AgentResult.Status.FAILED, outputs,
                    "required agent outputs are missing: " + String.join(", ", missing));
        }
        return new AgentResult(AgentResult.Status.COMPLETED, outputs, "agent state completeness review passed");
    }
}
