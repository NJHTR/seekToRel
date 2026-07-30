package com.reportai.llm.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.llm.ChatMessage;
import com.reportai.llm.ChatRequest;
import com.reportai.llm.ChatResponse;
import com.reportai.llm.LlmRequestBudget;
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

class OpenAiCompatibleLlmProviderTest {
    @Test
    void callsCompatibleChatEndpointWithoutLeakingCredentials() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":1,\"total_tokens\":3}}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        try (OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider("test",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "model", "secret",
                Duration.ofSeconds(2), Duration.ofSeconds(2), new ObjectMapper())) {
            ChatResponse response = provider.chat(new ChatRequest("model", List.of(
                    new ChatMessage(ChatMessage.Role.USER, "hello")), Map.of(), new LlmRequestBudget(100, 100, Duration.ofSeconds(2)), "r1"));
            assertEquals("ok", response.content());
            assertEquals(3, response.usage().totalTokens());
        } finally {
            server.stop(0);
        }
    }
}
