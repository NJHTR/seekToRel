package com.reportai.llm.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.llm.Capability;
import com.reportai.llm.ChatMessage;
import com.reportai.llm.ChatContentPart;
import com.reportai.llm.ChatRequest;
import com.reportai.llm.ChatResponse;
import com.reportai.llm.EmbeddingResponse;
import com.reportai.llm.LlmException;
import com.reportai.llm.LlmProvider;
import com.reportai.llm.OcrInput;
import com.reportai.llm.OcrPage;
import com.reportai.llm.OcrRequest;
import com.reportai.llm.OcrResponse;
import com.reportai.llm.SecretValue;
import com.reportai.llm.TokenUsage;
import com.reportai.llm.ToolCall;
import com.reportai.llm.ToolCallRequest;
import com.reportai.llm.ToolCallResponse;
import com.reportai.llm.ToolDefinition;
import com.reportai.llm.UnsupportedCapabilityException;
import com.reportai.llm.VisionInput;
import com.reportai.llm.VisionRequest;
import com.reportai.llm.VisionResponse;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpTimeoutException;
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
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider for OpenAI-compatible chat endpoints (OpenAI, DeepSeek, Qwen, GLM and
 * compatible gateways). Vision and OCR are transported through the same
 * OpenAI image_url chat content contract; embeddings remain unsupported.
 */
public final class OpenAiCompatibleLlmProvider implements LlmProvider, AutoCloseable {
    private static final int MAX_RESPONSE_BYTES = 10 * 1024 * 1024;
    private static final Set<String> SENSITIVE_OPTION_NAMES = Set.of(
            "api_key", "apikey", "access_key", "secret", "secret_key", "token", "password");
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiCompatibleLlmProvider.class);

    private final String providerId;
    private final URI endpoint;
    private final String model;
    private final SecretValue apiKey;
    private final String defaultReasoningEffort;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration readTimeout;

    public OpenAiCompatibleLlmProvider(String providerId, URI endpoint, String model, String apiKey,
                                       Duration connectTimeout, Duration readTimeout, ObjectMapper objectMapper) {
        this(providerId, endpoint, model, apiKey, connectTimeout, readTimeout, null, objectMapper);
    }

    public OpenAiCompatibleLlmProvider(String providerId, URI endpoint, String model, String apiKey,
                                       Duration connectTimeout, Duration readTimeout, String reasoningEffort,
                                       ObjectMapper objectMapper) {
        this.providerId = requireToken(providerId, "providerId");
        this.endpoint = normalizeEndpoint(Objects.requireNonNull(endpoint, "endpoint"));
        this.model = requireToken(model, "model");
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : new SecretValue(apiKey);
        this.defaultReasoningEffort = normalizeReasoningEffort(reasoningEffort);
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
        // The endpoint is OpenAI-compatible and accepts the standard image_url
        // content parts.  OCR deliberately reuses this same vision path so a
        // provider needs only one credential and one audited network boundary.
        return Set.copyOf(EnumSet.of(Capability.CHAT, Capability.TOOL_CALL,
                Capability.VISION, Capability.OCR));
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        Objects.requireNonNull(request, "request");
        Map<String, Object> payload = buildChatPayload(request.messages(), request.model(), request.options(), List.of());
        // Streaming keeps an intermediary/proxy connection active while a model
        // is reasoning. Providers that do not implement streaming may still
        // return a normal JSON response; post() accepts both representations.
        payload.put("stream", true);
        JsonNode root = post(payload,
                request.budget().timeout(), request.requestId());
        return toChatResponse(root);
    }

    @Override
    public VisionResponse vision(VisionRequest request) {
        Objects.requireNonNull(request, "request");
        List<Map<String, Object>> parts = new ArrayList<>();
        if (request.instruction() != null && !request.instruction().isBlank()) {
            parts.add(Map.of("type", "text", "text", request.instruction()));
        }
        for (VisionInput input : request.inputs()) {
            if (input.text() != null && !input.text().isBlank()) {
                parts.add(Map.of("type", "text", "text", input.text()));
            } else if (input.data() != null) {
                parts.add(Map.of("type", "image_url", "image_url", Map.of(
                        "url", dataUri(input.mediaType(), input.data()))));
            } else if (input.uri() != null) {
                parts.add(Map.of("type", "image_url", "image_url", Map.of(
                        "url", input.uri().toString())));
            }
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("vision request contains no content");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", request.model() == null || request.model().isBlank() ? model : request.model());
        payload.put("messages", List.of(Map.of("role", "user", "content", parts)));
        payload.put("max_completion_tokens", request.budget().maxOutputTokens());
        if (defaultReasoningEffort != null) {
            payload.put("reasoning_effort", defaultReasoningEffort);
        }
        // Vision requests are bounded and non-streaming.  This keeps the binary
        // input in one auditable request and avoids partial OCR text being
        // mistaken for a complete transcription.
        payload.put("stream", false);
        JsonNode root = post(payload, request.budget().timeout(), request.requestId());
        ChatResponse response = toChatResponse(root);
        return new VisionResponse(response.providerId(), response.model(), response.content(),
                List.of(), response.usage(), Map.of("remote", true, "transport", "chat-completions"));
    }

    @Override
    public OcrResponse ocr(OcrRequest request) {
        Objects.requireNonNull(request, "request");
        List<VisionInput> inputs = request.inputs().stream()
                .map(input -> input.data() == null
                        ? new VisionInput(input.mediaType(), null, input.uri(), null)
                        : new VisionInput(input.mediaType(), input.data(), null, null))
                .toList();
        String language = request.languageHint() == null || request.languageHint().isBlank()
                ? "the source language"
                : request.languageHint();
        VisionResponse response = vision(new VisionRequest(request.model(),
                "Transcribe every visible character in each supplied image. Preserve line breaks, table cell order, numbers, punctuation, and Chinese text. "
                        + "Return only the transcription, with an explicit '[image N]' heading for each image. "
                        + "Do not infer or correct text that is not legible. Language hint: " + language,
                inputs, request.budget(), request.requestId()));
        // The provider does not expose per-line confidence.  Zero means
        // confidence is unknown, rather than falsely claiming certainty.
        return new OcrResponse(response.providerId(), response.model(),
                List.of(new OcrPage(1, response.content(), 0.0)), response.usage());
    }

    @Override
    public ToolCallResponse toolCall(ToolCallRequest request) {
        Objects.requireNonNull(request, "request");
        List<Map<String, Object>> tools = request.tools().stream().map(OpenAiCompatibleLlmProvider::toolPayload).toList();
        JsonNode root = post(buildChatPayload(request.messages(), request.model(), request.options(), tools),
                request.budget().timeout(), request.requestId());
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
        payload.put("messages", messages.stream()
                .map(OpenAiCompatibleLlmProvider::messagePayload)
                .toList());
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
        if (defaultReasoningEffort != null && !payload.containsKey("reasoning_effort")) {
            payload.put("reasoning_effort", defaultReasoningEffort);
        }
        return payload;
    }

    /** Maps both legacy string messages and OpenAI multimodal content arrays. */
    private static Map<String, Object> messagePayload(ChatMessage message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("role", message.role().name().toLowerCase(Locale.ROOT));
        if (!message.isMultimodal()) {
            payload.put("content", message.content());
            return payload;
        }
        List<Map<String, Object>> parts = new ArrayList<>();
        if (message.content() != null && !message.content().isBlank()) {
            parts.add(ChatContentPart.text(message.content()).toOpenAiPayload());
        }
        message.contentParts().forEach(part -> parts.add(part.toOpenAiPayload()));
        payload.put("content", List.copyOf(parts));
        return payload;
    }

    private JsonNode post(Map<String, Object> payload, Duration timeout, String requestId) {
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
        String safeRequestId = safeRequestId(requestId);
        if (!safeRequestId.isBlank()) {
            builder.header("X-Request-Id", safeRequestId);
        }
        if (apiKey != null) {
            char[] secret = apiKey.revealCopy();
            try {
                builder.header("Authorization", "Bearer " + new String(secret));
            } finally {
                Arrays.fill(secret, '\0');
            }
        }
        long started = System.nanoTime();
        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.body() != null && response.body().length > MAX_RESPONSE_BYTES) {
                throw new LlmException("LLM_RESPONSE_TOO_LARGE", "LLM response exceeds the maximum allowed size");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                int status = response.statusCode();
                LOGGER.warn("llm provider request failed provider={} status={} elapsedMs={} requestId={}",
                        providerId, status, elapsedMillis(started), safeRequestId);
                throw new LlmException("LLM_PROVIDER_HTTP_" + status,
                        "LLM provider returned HTTP " + status);
            }
            LOGGER.info("llm provider request completed provider={} status={} elapsedMs={} requestId={}",
                    providerId, response.statusCode(), elapsedMillis(started), safeRequestId);
            byte[] responseBody = response.body() == null ? new byte[0] : response.body();
            String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
            String textBody = new String(responseBody, java.nio.charset.StandardCharsets.UTF_8);
            if (contentType.contains("text/event-stream") || textBody.stripLeading().startsWith("data:")) {
                return parseServerSentEvents(textBody);
            }
            return objectMapper.readTree(responseBody);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM_INTERRUPTED", "LLM request was interrupted", exception);
        } catch (HttpTimeoutException exception) {
            LOGGER.warn("llm provider request timed out provider={} elapsedMs={} requestId={}",
                    providerId, elapsedMillis(started), safeRequestId);
            throw new LlmException("LLM_TIMEOUT", "LLM provider request timed out", exception);
        } catch (IOException exception) {
            LOGGER.warn("llm provider request I/O failure provider={} elapsedMs={} requestId={}",
                    providerId, elapsedMillis(started), safeRequestId);
            throw new LlmException("LLM_IO_ERROR", "LLM provider request failed", exception);
        }
    }

    /** Converts OpenAI-compatible chat SSE deltas into the normal response shape. */
    private JsonNode parseServerSentEvents(String body) {
        StringBuilder content = new StringBuilder();
        String finishReason = "";
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;
        try (BufferedReader reader = new BufferedReader(new StringReader(body))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring("data:".length()).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) continue;
                JsonNode chunk = objectMapper.readTree(data);
                JsonNode choice = chunk.path("choices").path(0);
                String delta = contentText(choice.path("delta").path("content"));
                if (!delta.isEmpty()) content.append(delta);
                if (!choice.path("finish_reason").isMissingNode() && !choice.path("finish_reason").isNull()) {
                    finishReason = choice.path("finish_reason").asText("");
                }
                JsonNode usage = chunk.path("usage");
                if (usage.isObject()) {
                    promptTokens = usage.path("prompt_tokens").asInt(promptTokens);
                    completionTokens = usage.path("completion_tokens").asInt(completionTokens);
                    totalTokens = usage.path("total_tokens").asInt(promptTokens + completionTokens);
                }
            }
        } catch (IOException failure) {
            throw new LlmException("LLM_INVALID_RESPONSE", "LLM provider returned malformed SSE", failure);
        }
        com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ArrayNode choices = root.putArray("choices");
        com.fasterxml.jackson.databind.node.ObjectNode choice = choices.addObject();
        choice.put("finish_reason", finishReason);
        choice.putObject("message").put("content", content.toString());
        com.fasterxml.jackson.databind.node.ObjectNode usage = root.putObject("usage");
        usage.put("prompt_tokens", promptTokens);
        usage.put("completion_tokens", completionTokens);
        usage.put("total_tokens", totalTokens == 0 ? promptTokens + completionTokens : totalTokens);
        return root;
    }

    private static long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private static String dataUri(String mediaType, byte[] bytes) {
        if (mediaType == null || mediaType.isBlank() || bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("vision image data is invalid");
        }
        return "data:" + mediaType.strip().toLowerCase(Locale.ROOT) + ";base64,"
                + java.util.Base64.getEncoder().encodeToString(bytes);
    }

    private static String safeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 128
                || !requestId.matches("[A-Za-z0-9][A-Za-z0-9._:-]*")) {
            return "";
        }
        return requestId;
    }

    private ChatResponse toChatResponse(JsonNode root) {
        JsonNode choice = firstChoice(root);
        JsonNode message = choice.path("message");
        return new ChatResponse(providerId, model, contentText(message.path("content")),
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
        return new ToolCallResponse(providerId, model, contentText(message.path("content")), calls,
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

    /** Accepts both legacy string content and gateways that return text parts. */
    private static String contentText(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isTextual()) {
            return value.textValue();
        }
        if (value.isArray()) {
            StringBuilder result = new StringBuilder();
            for (JsonNode part : value) {
                String text = contentText(part);
                if (!text.isEmpty()) {
                    result.append(text);
                }
            }
            return result.toString();
        }
        if (value.isObject()) {
            JsonNode text = value.get("text");
            if (text != null && !text.isNull()) {
                return contentText(text);
            }
            JsonNode content = value.get("content");
            if (content != null && !content.isNull()) {
                return contentText(content);
            }
        }
        return value.asText("");
    }

    private static boolean isSensitive(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return SENSITIVE_OPTION_NAMES.contains(normalized)
                || normalized.endsWith("_api_key")
                || normalized.endsWith("_secret")
                || normalized.endsWith("_secret_key")
                || normalized.endsWith("_password")
                || normalized.endsWith("_token");
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

    private static String normalizeReasoningEffort(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("low", "medium", "high").contains(normalized)) {
            throw new IllegalArgumentException("reasoningEffort must be low, medium or high");
        }
        return normalized;
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
