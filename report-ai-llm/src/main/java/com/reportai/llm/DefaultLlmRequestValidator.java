package com.reportai.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DefaultLlmRequestValidator implements LlmRequestValidator {
    private static final List<String> SENSITIVE_OPTION_NAMES = List.of(
            "api_key", "apikey", "access_key", "secret", "secret_key", "token", "password");

    private final LlmRequestPolicy policy;
    private final TokenEstimator tokenEstimator;

    public DefaultLlmRequestValidator() {
        this(LlmRequestPolicy.safeDefaults(), TokenEstimator::approximate);
    }

    public DefaultLlmRequestValidator(LlmRequestPolicy policy, TokenEstimator tokenEstimator) {
        this.policy = policy == null ? LlmRequestPolicy.safeDefaults() : policy;
        this.tokenEstimator = tokenEstimator == null ? TokenEstimator::approximate : tokenEstimator;
    }

    @Override
    public void validate(LlmRequest request) {
        List<String> violations = new ArrayList<>();
        if (request == null) {
            violations.add("request must not be null");
        } else {
            if (request.budget() == null) {
                violations.add("budget must not be null");
            } else {
                if (request.budget().maxInputTokens() > policy.maxInputTokens()) {
                    violations.add("requested input token budget exceeds server policy");
                }
                if (request.budget().maxOutputTokens() > policy.maxOutputTokens()) {
                    violations.add("requested output token budget exceeds server policy");
                }
                Duration timeout = request.budget().timeout();
                if (timeout.compareTo(policy.maxTimeout()) > 0) {
                    violations.add("requested timeout exceeds server policy");
                }
            }
            if (request.requestId() != null && request.requestId().length() > 128) {
                violations.add("requestId is too long");
            }
            int estimated = estimate(request);
            if (estimated > policy.maxInputTokens()) {
                violations.add("estimated input tokens exceed server policy");
            }
            if (request instanceof ChatRequest chat) {
                if (chat.messages().size() > policy.maxMessages()) {
                    violations.add("message count exceeds server policy");
                }
                checkSensitiveOptions(chat.options(), violations);
            } else if (request instanceof ToolCallRequest toolCall) {
                if (toolCall.messages().size() > policy.maxMessages()) {
                    violations.add("message count exceeds server policy");
                }
                if (toolCall.maxToolCalls() > policy.maxToolCalls()) {
                    violations.add("tool call count exceeds server policy");
                }
                checkSensitiveOptions(toolCall.options(), violations);
            } else if (request instanceof VisionRequest vision) {
                if (vision.inputs().size() > policy.maxBatchSize()) {
                    violations.add("vision input count exceeds server policy");
                }
                if (vision.inputs().stream().anyMatch(input -> input.data() != null
                        && input.data().length > policy.maxImageBytes())) {
                    violations.add("vision image size exceeds server policy");
                }
            } else if (request instanceof EmbeddingRequest embedding
                    && embedding.inputs().size() > policy.maxBatchSize()) {
                violations.add("embedding batch size exceeds server policy");
            } else if (request instanceof OcrRequest ocr && ocr.inputs().size() > policy.maxBatchSize()) {
                violations.add("OCR input count exceeds server policy");
            }
        }
        if (!violations.isEmpty()) {
            throw new LlmValidationException(violations);
        }
    }

    private int estimate(LlmRequest request) {
        if (request instanceof ChatRequest chat) {
            return tokenEstimator.estimate(chat.messages().stream().map(ChatMessage::content).toList());
        }
        if (request instanceof ToolCallRequest toolCall) {
            return tokenEstimator.estimate(toolCall.messages().stream().map(ChatMessage::content).toList());
        }
        if (request instanceof VisionRequest vision) {
            return tokenEstimator.estimate(vision.instruction());
        }
        if (request instanceof EmbeddingRequest embedding) {
            return tokenEstimator.estimate(embedding.inputs());
        }
        return request.estimatedInputTokens();
    }

    private static void checkSensitiveOptions(Map<String, Object> options, List<String> violations) {
        if (options == null) {
            return;
        }
        options.keySet().stream().filter(key -> key != null).map(String::toLowerCase)
                .filter(key -> SENSITIVE_OPTION_NAMES.stream().anyMatch(key::contains))
                .forEach(key -> violations.add("credentials must not be supplied in request options"));
    }
}
