package com.reportai.llm.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.llm.ChatMessage;
import com.reportai.llm.ChatContentPart;
import com.reportai.llm.ChatRequest;
import com.reportai.llm.ChatResponse;
import com.reportai.llm.LlmRequestBudget;
import com.reportai.llm.LlmException;
import com.reportai.llm.VisionInput;
import com.reportai.llm.VisionRequest;
import com.reportai.llm.VisionResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiCompatibleLlmProviderTest {
    @Test
    void mapsHttpTimeoutToStableProviderCode() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        try (OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider("test",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "model", "secret",
                Duration.ofSeconds(1), Duration.ofMillis(50), new ObjectMapper())) {
            ChatRequest request = new ChatRequest("model", List.of(
                    new ChatMessage(ChatMessage.Role.USER, "hello")), Map.of(),
                    new LlmRequestBudget(100, 100, Duration.ofSeconds(1)), "timeout-test");

            LlmException failure = assertThrows(LlmException.class, () -> provider.chat(request));

            assertEquals("LLM_TIMEOUT", failure.code());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void callsCompatibleChatEndpointWithoutLeakingCredentials() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes());
            if (!requestBody.contains("\"reasoning_effort\":\"high\"")
                    || !requestBody.contains("\"max_completion_tokens\":256")
                    || requestBody.contains("api_key")
                    || requestBody.contains("password")) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":1,\"total_tokens\":3}}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        try (OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider("test",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "model", "secret",
                Duration.ofSeconds(2), Duration.ofSeconds(2), "high", new ObjectMapper())) {
            ChatResponse response = provider.chat(new ChatRequest("model", List.of(
                    new ChatMessage(ChatMessage.Role.USER, "hello")),
                    Map.of("max_completion_tokens", 256, "api_key", "must-not-forward"),
                    new LlmRequestBudget(100, 100, Duration.ofSeconds(2)), "r1"));
            assertEquals("ok", response.content());
            assertEquals(3, response.usage().totalTokens());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void assemblesOpenAiCompatibleChatSseDeltas() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes());
            if (!requestBody.contains("\"stream\":true")) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }
            byte[] body = ("data: {\"choices\":[{\"delta\":{\"content\":\"hel\"}}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"lo\"},\"finish_reason\":\"stop\"}]}\n\n"
                    + "data: [DONE]\n\n").getBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try (OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider("test",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "model", "secret",
                Duration.ofSeconds(2), Duration.ofSeconds(2), new ObjectMapper())) {
            ChatResponse response = provider.chat(new ChatRequest("model", List.of(
                    new ChatMessage(ChatMessage.Role.USER, "hello")), Map.of(),
                    new LlmRequestBudget(100, 100, Duration.ofSeconds(2)), "sse-1"));
            assertEquals("hello", response.content());
            assertEquals("stop", response.finishReason());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsProviderHttpStatusToSafeDiagnosticCode() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = "provider-secret-must-not-be-exposed".getBytes();
            exchange.sendResponseHeaders(400, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try (OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider("test",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "model", "secret",
                Duration.ofSeconds(1), Duration.ofSeconds(1), new ObjectMapper())) {
            ChatRequest request = new ChatRequest("model", List.of(
                    new ChatMessage(ChatMessage.Role.USER, "hello")), Map.of(),
                    new LlmRequestBudget(100, 100, Duration.ofSeconds(1)), "http-status-test");

            LlmException failure = assertThrows(LlmException.class, () -> provider.chat(request));

            assertEquals("LLM_PROVIDER_HTTP_400", failure.code());
            org.junit.jupiter.api.Assertions.assertFalse(failure.getMessage().contains("provider-secret"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsOpenAiImageUrlContentWithoutChangingTextMessageShape() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes());
            var root = new ObjectMapper().readTree(requestBody);
            var content = root.path("messages").path(0).path("content");
            if (!content.isArray() || !"text".equals(content.path(0).path("type").asText())
                    || !"image_url".equals(content.path(1).path("type").asText())
                    || !"data:image/png;base64,AQID".equals(content.path(1).path("image_url")
                    .path("url").asText())) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"seen\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":1,\"total_tokens\":4}}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try (OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider("test",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "model", "secret",
                Duration.ofSeconds(2), Duration.ofSeconds(2), new ObjectMapper())) {
            ChatMessage message = ChatMessage.multimodal(ChatMessage.Role.USER, "Read this image",
                    List.of(ChatContentPart.image("image/png", new byte[]{1, 2, 3})));
            ChatResponse response = provider.chat(new ChatRequest("model", List.of(message), Map.of(),
                    new LlmRequestBudget(100, 100, Duration.ofSeconds(2)), "vision-test"));
            assertEquals("seen", response.content());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readsTextFromArrayShapedAssistantContent() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = "{\"choices\":[{\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"part-a\"},{\"type\":\"text\",\"text\":\"part-b\"}]},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try (OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider("test",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "model", "secret",
                Duration.ofSeconds(2), Duration.ofSeconds(2), new ObjectMapper())) {
            ChatResponse response = provider.chat(new ChatRequest("model", List.of(
                    new ChatMessage(ChatMessage.Role.USER, "hello")), Map.of(),
                    new LlmRequestBudget(100, 100, Duration.ofSeconds(2)), "array-content-test"));
            assertEquals("part-apart-b", response.content());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsVisionRequestAsOpenAiImageUrlAndMapsResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes());
            var root = new ObjectMapper().readTree(requestBody);
            var message = root.path("messages").path(0);
            var content = message.path("content");
            if (!"false".equals(root.path("stream").asText()) || !content.isArray()
                    || !"image_url".equals(content.path(1).path("type").asText())) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"WBS text\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2,\"total_tokens\":5}}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try (OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider("test",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "model", "secret",
                Duration.ofSeconds(2), Duration.ofSeconds(2), new ObjectMapper())) {
            VisionResponse response = provider.vision(new VisionRequest("model", "transcribe",
                    List.of(new VisionInput("image/png", new byte[]{1, 2, 3}, null, null)),
                    new LlmRequestBudget(100, 100, Duration.ofSeconds(2)), "vision-request-test"));
            assertEquals("WBS text", response.content());
            assertEquals(5, response.usage().totalTokens());
        } finally {
            server.stop(0);
        }
    }
}
