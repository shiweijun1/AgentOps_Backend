package com.agentops.agent.infrastructure.model;

import com.agentops.agent.application.TicketAnalysisModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** OpenAI-compatible chat-completions HTTP endpoint; no provider SDK in Agent domain. */
@Component
@ConditionalOnProperty(prefix = "agentops.ai", name = "provider", havingValue = "http")
public class HttpTicketAnalysisModel implements TicketAnalysisModel {
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String key;
    private final String model;
    private final Duration timeout;

    public HttpTicketAnalysisModel(ObjectMapper mapper,
            @Value("${agentops.ai.base-url}") String baseUrl,
            @Value("${agentops.ai.api-key}") String key,
            @Value("${agentops.ai.model}") String model,
            @Value("${agentops.ai.request-timeout:30s}") Duration timeout) {
        if (baseUrl.isBlank() || key.isBlank() || model.isBlank()) {
            throw new IllegalArgumentException("HTTP AI requires base-url, api-key and model");
        }
        this.mapper = mapper; this.endpoint = URI.create(baseUrl); this.key = key;
        this.model = model; this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public ModelResponse analyze(ModelRequest input) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "model", model, "response_format", Map.of("type", "json_object"),
                "messages", new Object[] {
                        Map.of("role", "system", "content", "Analyze the support ticket. Return ONLY one JSON object with exactly: categoryCode (ACCOUNT|PAYMENT|NETWORK|SOFTWARE|OTHER), priority (LOW|MEDIUM|HIGH|URGENT), sentiment (POSITIVE|NEUTRAL|NEGATIVE), riskLevel (LOW|MEDIUM|HIGH), confidence (0..1), manualRequired (boolean), manualReason (string or null), reason (short string). No markdown."),
                        Map.of("role", "user", "content", "Title: " + input.title() + "\nDescription: " + input.description())
                }));
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("AI HTTP status " + response.statusCode());
        }
        JsonNode root = mapper.readTree(response.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (!content.isTextual()) throw new IllegalStateException("AI response has no content");
        return new ModelResponse(content.asText(), model,
                root.path("usage").path("prompt_tokens").asInt(0),
                root.path("usage").path("completion_tokens").asInt(0));
    }

    @Override public String provider() { return "http"; }
}
