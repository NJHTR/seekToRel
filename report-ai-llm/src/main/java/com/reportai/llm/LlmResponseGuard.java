package com.reportai.llm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LlmResponseGuard {
    private static final int MAX_ARGUMENT_DEPTH = 8;
    private static final int MAX_ARGUMENT_ENTRIES = 256;
    private static final int MAX_ARGUMENT_STRING_CHARS = 64 * 1024;
    private static final int MAX_CHAT_CONTENT_CHARS = 512 * 1024;

    private LlmResponseGuard() {
    }

    public static void validate(ChatRequest request, ChatResponse response) {
        List<String> violations = new ArrayList<>();
        if (response == null) {
            violations.add("response must not be null");
        } else if (response.content().length() > MAX_CHAT_CONTENT_CHARS) {
            // Providers disagree on whether completion_tokens includes hidden
            // reasoning tokens. Treat usage as telemetry and enforce the
            // provider-independent content-size boundary here instead. A
            // successful stop response must not be discarded merely because a
            // reasoning gateway reports a larger aggregate completion count.
            violations.add("response content exceeds the maximum allowed size");
        }
        if (!violations.isEmpty()) {
            throw new LlmValidationException(violations);
        }
    }

    /** Validates provider tool calls against the request's declared tools and server budgets. */
    public static void validate(ToolCallRequest request, ToolCallResponse response) {
        List<String> violations = new ArrayList<>();
        if (response == null) {
            violations.add("response must not be null");
        } else {
            if (response.toolCalls().size() > request.maxToolCalls()) {
                violations.add("tool call count exceeds the permitted budget");
            }
            Set<String> declaredNames = request.tools().stream().map(ToolDefinition::name)
                    .collect(java.util.stream.Collectors.toSet());
            Set<String> callIds = new HashSet<>();
            for (ToolCall call : response.toolCalls()) {
                if (call == null) {
                    violations.add("tool call must not be null");
                    continue;
                }
                if (!declaredNames.contains(call.name())) {
                    violations.add("tool call is not declared: " + call.name());
                }
                if (!callIds.add(call.id())) {
                    violations.add("tool call id is duplicated: " + call.id());
                }
                ToolDefinition definition = request.tools().stream()
                        .filter(candidate -> candidate.name().equals(call.name()))
                        .findFirst().orElse(null);
                if (definition != null) {
                    validateArguments(call.arguments(), definition.inputSchema(), violations);
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new LlmValidationException(violations);
        }
    }

    private static void validateArguments(Map<String, Object> arguments, Map<String, Object> schema,
                                          List<String> violations) {
        if (arguments == null) {
            violations.add("tool arguments must be an object");
            return;
        }
        ArgumentStats stats = new ArgumentStats();
        inspect(arguments, 0, stats, violations);
        if (stats.entries > MAX_ARGUMENT_ENTRIES) {
            violations.add("tool arguments contain too many values");
        }
        validateObjectSchema(arguments, schema, violations);
    }

    private static void inspect(Object value, int depth, ArgumentStats stats, List<String> violations) {
        if (depth > MAX_ARGUMENT_DEPTH) {
            violations.add("tool arguments exceed the maximum nesting depth");
            return;
        }
        if (stats.entries > MAX_ARGUMENT_ENTRIES) {
            return;
        }
        if (value instanceof String text) {
            stats.entries++;
            if (text.length() > MAX_ARGUMENT_STRING_CHARS) {
                violations.add("tool argument string is too long");
            }
            return;
        }
        if (value instanceof Double number && !Double.isFinite(number)) {
            violations.add("tool argument number must be finite");
            stats.entries++;
            return;
        }
        if (value instanceof Float number && !Float.isFinite(number)) {
            violations.add("tool argument number must be finite");
            stats.entries++;
            return;
        }
        if (value instanceof Map<?, ?> map) {
            stats.entries += map.size();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    violations.add("tool argument keys must be strings");
                }
                inspect(entry.getValue(), depth + 1, stats, violations);
            }
            return;
        }
        if (value instanceof Iterable<?> values) {
            for (Object item : values) {
                inspect(item, depth + 1, stats, violations);
            }
        }
    }

    private static void validateObjectSchema(Map<String, Object> arguments, Map<String, Object> schema,
                                             List<String> violations) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        Object type = schema.get("type");
        if (type != null && !"object".equals(type)) {
            violations.add("tool input schema must describe an object");
            return;
        }
        Object required = schema.get("required");
        if (required instanceof Iterable<?> fields) {
            for (Object field : fields) {
                if (field instanceof String name && !arguments.containsKey(name)) {
                    violations.add("required tool argument is missing: " + name);
                }
            }
        }
        Object properties = schema.get("properties");
        if (!(properties instanceof Map<?, ?> propertyMap)) {
            return;
        }
        if (Boolean.FALSE.equals(schema.get("additionalProperties"))) {
            for (String name : arguments.keySet()) {
                if (!propertyMap.containsKey(name)) {
                    violations.add("tool argument is not allowed: " + name);
                }
            }
        }
        for (Map.Entry<String, Object> argument : arguments.entrySet()) {
            Object propertySchema = propertyMap.get(argument.getKey());
            if (propertySchema instanceof Map<?, ?> property && property.get("type") instanceof String expected
                    && !matchesType(argument.getValue(), expected)) {
                violations.add("tool argument has invalid type: " + argument.getKey());
            }
        }
    }

    private static boolean matchesType(Object value, String expected) {
        return switch (expected) {
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number;
            case "integer" -> value instanceof Byte || value instanceof Short || value instanceof Integer
                    || value instanceof Long;
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof Iterable<?>;
            case "null" -> value == null;
            default -> true;
        };
    }

    private static final class ArgumentStats {
        private int entries;
    }
}
