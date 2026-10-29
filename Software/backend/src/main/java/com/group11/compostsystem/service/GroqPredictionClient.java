package com.group11.compostsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.group11.compostsystem.service.GeminiPredictionClient.PredictionUnavailableException;

@Component
public class GroqPredictionClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(GroqPredictionClient.class);
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final String apiKey;
    private final String model;

    @Autowired
    public GroqPredictionClient(ObjectMapper mapper,
            @Value("${groq.api.key:}") String apiKey,
            @Value("${groq.model:openai/gpt-oss-120b}") String model) {
        this(mapper, apiKey, model, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    GroqPredictionClient(ObjectMapper mapper, String apiKey, String model, HttpClient client) {
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.client = client;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && model.matches("[a-zA-Z0-9._/-]{1,90}");
    }

    public String modelName() {
        return model;
    }

    public String generate(String prompt) {
        if (!isConfigured()) throw unavailable();
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", 0.2,
                    "max_completion_tokens", 4096,
                    "response_format", Map.of("type", "json_schema", "json_schema",
                            Map.of("name", "compost_prediction", "strict", true, "schema", PredictionPayload.schema())));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("Groq request failed: HTTP {}, model={}. Check backend Groq access, quota, and availability.",
                        response.statusCode(), model);
                throw unavailable();
            }
            JsonNode root = mapper.readTree(response.body());
            if (root == null || !root.isObject()) throw new IOException("Invalid Groq response");
            JsonNode choice = root.path("choices").path(0);
            JsonNode message = choice.path("message");
            if (!"stop".equals(choice.path("finish_reason").asText())
                    || !message.path("content").isTextual()
                    || message.path("content").asText().isBlank()
                    || !message.path("refusal").asText("").isBlank()) {
                throw unavailable();
            }
            return message.path("content").asText();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException ex) {
            LOGGER.warn("Groq connection or response failed: {}", ex.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private PredictionUnavailableException unavailable() {
        return new PredictionUnavailableException("The backup AI service is unavailable. Please try again later. Your daily prediction has not been used.");
    }
}
