package com.reportai.llm.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.llm.Capability;
import com.reportai.llm.ChatMessage;
import com.reportai.llm.ChatRequest;
import com.reportai.llm.ChatResponse;
import com.reportai.llm.EmbeddingResponse;
import com.reportai.llm.LlmException;
import com.reportai.llm.LlmProvider;
import com.reportai.llm.SecretValue;
import com.reportai.llm.TokenUsage;
import com.reportai.llm.ToolCall;
import com.reportai.llm.ToolCallRequest;
import com.reportai.llm.ToolCallResponse;
import com.reportai.llm.ToolDefinition;
import com.reportai.llm.UnsupportedCapabilityException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Arrays;

/**
 * Provider for OpenAI-compatible chat endpoints (OpenAI, DeepSeek, Qwen, GLM and
 * compatible gateways). It intentionally exposes only CHAT and TOOL_CALL until
 * provider-specific vision/embedding contracts are implemented.
 */
public final class OpenAiCompatibleLlmProvider implements LlmProvider, AutoCloseable {
    private static final int MAX_RESPONSE_BYTES = 10 * 1024 * 1024;

    private final String providerId;
    private final URI endpoint;
    private final String model;
    private final SecretValue apiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration readTimeout;

    public OpenAiCompatibleLlmProvider(String providerId, URI endpoint, String model, String apiKey,
                                       Duration connectTimeout, Duration readTimeout, ObjectMapper objectMapper) {
        this.providerId = requireToken(providerId, "providerId");
        this.endpoint = normalizeEndpoint(Objects.requireNonNull(endpoint, "endpoint"));
        this.model = requireToken(model, "model");
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : new SecretValue(apiKey);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        Duration connect = requireTimeout(connectTimeout, "connectTimeout");
        this.readTimeout = requireTimeout(readTimeout, "readTimeout");
        this.httpClient = HttpClient.newBuilder().connectTimeout(connect).build();
    }

    @Override
    public String providerId() {
        return providerId;
    }

    @Override
    public Set<Capability> capabilities() {
        return Set.copyOf(EnumSet.of(Capability.CHAT, Capability.TOOL_CALL));
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        Objects.requireNonNull(request, "request");
        JsonNode root = post(buildChatPayload(request.messages(), request.model(), request.options(), List.of()),
                request.budget().timeout());
        return toChatResponse(root);
    }

    @Override
    public ToolCallResponse toolCall(ToolCallRequest request) {
        Objects.requireNonNull(request, "request");
        List<Map<String, Object>> tools = request.tools().stream().map(OpenAiCompatibleLlmProvider::toolPayload).toList();
        JsonNode root = post(buildChatPayload(request.messages(), request.model(), request.options(), tools),
                request.budget().timeout());
        return toToolCallResponse(root);
    }

    @Override
    public EmbeddingResponse embedding(com.reportai.llm.EmbeddingRequest request) {
        throw new UnsupportedCapabilityException(providerId, Capability.EMBEDDING);
    }

    private Map<String, Object> buildChatPayload(List<ChatMessage> messages, String requestedModel,
                                                   Map<String, Object> options, List<Map<String, Object>> tools) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", requestedModel == null || requestedModel.isBlank() ? model : requestedModel);
        payload.put("messages", messages.stream().map(message -> Map.of(
                "role", message.role().name().toLowerCase(), "content", message.content())).toList());
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
            payload.put("tool_choice", "auto");
        }
        if (options != null) {
            options.forEach((key, value) -> {
                if (key != null && value != null && !isSensitive(key)) {
                    payload.put(key, value);
                }
            });
        }
        return payload;
    }

    private JsonNode post(Map<String, Object> payload, Duration timeout) {
        final String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new LlmException("LLM_SERIALIZATION_FAILED", "unable to serialize LLM request", exception);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(min(timeout, readTimeout))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Request-Source", "report-ai")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (apiKey != null) {
            char[] secret = apiKey.revealCopy();
            try {
                builder.header("Authorization", "Bearer " + new String(secret));
            } finally {
                Arrays.fill(secret, '\0');
            }
        }
        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.body() != null && response.body().length > MAX_RESPONSE_BYTES) {
                throw new LlmException("LLM_RESPONSE_TOO_LARGE", "LLM response exceeds the maximum allowed size");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LlmException("LLM_PROVIDER_ERROR", "LLM provider returned HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body() == null ? new byte[0] : response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM_INTERRUPTED", "LLM request was interrupted", exception);
        } catch (IOException exception) {
            throw new LlmException("LLM_IO_ERROR", "LLM provider request failed", exception);
        }
    }

    private ChatResponse toChatResponse(JsonNode root) {
        JsonNode choice = firstChoice(root);
        JsonNode message = choice.path("message");
        return new ChatResponse(providerId, model, text(message, "content"),
                text(choice, "finish_reason"), usage(root), Map.of("remote", true));
    }

    private ToolCallResponse toToolCallResponse(JsonNode root) {
        JsonNode choice = firstChoice(root);
        JsonNode message = choice.path("message");
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            String id = text(call, "id");
            JsonNode function = call.path("function");
            String name = text(function, "name");
            Map<String, Object> arguments = parseArguments(function.path("arguments").asText("{}"));
            if (!id.isBlank() && !name.isBlank()) {
                calls.add(new ToolCall(id, name, arguments));
            }
        }
        return new ToolCallResponse(providerId, model, text(message, "content"), calls,
                text(choice, "finish_reason"), usage(root), Map.of("remote", true));
    }

    private JsonNode firstChoice(JsonNode root) {
        JsonNode choices = root == null ? null : root.path("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new LlmException("LLM_INVALID_RESPONSE", "LLM provider returned no choices");
        }
        return choices.get(0);
    }

    private TokenUsage usage(JsonNode root) {
        JsonNode usage = root.path("usage");
        int input = usage.path("prompt_tokens").asInt(0);
        int output = usage.path("completion_tokens").asInt(0);
        int total = usage.path("total_tokens").asInt(input + output);
        return new TokenUsage(Math.max(0, input), Math.max(0, output), Math.max(0, total));
    }

    private static Map<String, Object> toolPayload(ToolDefinition definition) {
        return Map.of("type", "function", "function", Map.of(
                "name", definition.name(), "description", definition.description(), "parameters", definition.inputSchema()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String json) {
        try {
            JsonNode node = objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
            if (!node.isObject()) {
                return Map.of();
            }
            return objectMapper.convertValue(node, Map.class);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static boolean isSensitive(String key) {
        String normalized = key.toLowerCase();
        return normalized.contains("key") || normalized.contains("secret")
                || normalized.contains("token") || normalized.contains("password");
    }

    private static URI normalizeEndpoint(URI endpoint) {
        String scheme = endpoint.getScheme();
        if (scheme == null || (!(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))
                || endpoint.getHost() == null || endpoint.getUserInfo() != null) {
            throw new IllegalArgumentException("endpoint must be an http(s) URL without credentials");
        }
        String value = endpoint.toString();
        if (!value.endsWith("/chat/completions")) {
            value = value.endsWith("/") ? value + "chat/completions" : value + "/chat/completions";
        }
        return URI.create(value);
    }

    private static Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static Duration requireTimeout(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException(name + " must be between 1ms and 10m");
        }
        return value;
    }

    private static String requireToken(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.trim();
    }

    @Override
    public void close() {
        if (apiKey != null) {
            apiKey.close();
        }
    }
}
