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
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class GeminiPredictionClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiPredictionClient.class);
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final String apiKey;
    private final String model;

    @Autowired
    public GeminiPredictionClient(ObjectMapper mapper,
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.model:gemini-2.5-flash}") String model) {
        this(mapper, apiKey, model, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build());
    }

    GeminiPredictionClient(ObjectMapper mapper, String apiKey, String model, HttpClient client) {
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.client = client;
        if (this.apiKey.isBlank()) {
            LOGGER.warn("Gemini is not configured. Set GEMINI_API_KEY on the backend service (not the frontend).");
        }
    }

    public String generate(String prompt) {
        if (apiKey.isBlank() || !model.matches("[a-zA-Z0-9._-]+")) {
            LOGGER.warn("Gemini configuration is incomplete. Check GEMINI_API_KEY and GEMINI_MODEL.");
            throw new PredictionUnavailableException(
                    "AI predictions aren't available yet. Please contact the system administrator.");
        }
        try {
            Map<String, Object> fields = Map.of(
                    "predicted_condition", Map.of("type", "STRING"),
                    "prediction_summary", Map.of("type", "STRING"),
                    "estimated_ready_date", Map.of("type", "STRING", "nullable", true),
                    "estimated_days_remaining", Map.of("type", "INTEGER", "nullable", true),
                    "recommendation", Map.of("type", "STRING"),
                    "trend_summary", Map.of("type", "STRING"),
                    "confidence_score", Map.of("type", "NUMBER"));
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of(
                            "temperature", 0.2,
                            "responseMimeType", "application/json",
                            "responseSchema", Map.of("type", "OBJECT", "properties", fields,
                                    "required", fields.keySet())));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                            + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                // Never log the key, request headers, or an unfiltered provider response.
                LOGGER.warn("Gemini request failed: HTTP {}, model={}. {}", response.statusCode(), model,
                        switch (response.statusCode()) {
                            case 400, 401, 403 -> "Check the API key, API restrictions, and project access.";
                            case 404 -> "Check GEMINI_MODEL availability for this API key.";
                            case 429 -> "Check the project's Gemini quota and rate limits.";
                            default -> "Check Gemini service availability.";
                        });
                throw new PredictionUnavailableException(switch (response.statusCode()) {
                    case 429 -> "The AI service has reached its usage limit. Please try again later. Your daily prediction has not been used.";
                    case 400, 401, 403, 404 -> "We couldn't connect to the AI service. Please contact the system administrator.";
                    default -> "The AI service is temporarily unavailable. Please try again shortly.";
                });
            }
            JsonNode candidate = mapper.readTree(response.body()).path("candidates").path(0);
            if (!"STOP".equals(candidate.path("finishReason").asText())) {
                throw new PredictionUnavailableException("The AI couldn't complete this prediction. Please try again. Your daily prediction has not been used.");
            }
            StringBuilder result = new StringBuilder();
            for (JsonNode part : candidate.path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false)) result.append(part.path("text").asText(""));
            }
            if (result.toString().isBlank()) throw new IOException("Empty Gemini response");
            return result.toString();
        } catch (HttpTimeoutException ex) {
            LOGGER.warn("Gemini request timed out (60-second request limit).");
            throw new PredictionUnavailableException("The AI is taking longer than expected. Please try again shortly. Your daily prediction has not been used.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PredictionUnavailableException("The prediction was interrupted. Please try again.");
        } catch (IOException ex) {
            LOGGER.warn("Gemini connection or response failed: {}", ex.getClass().getSimpleName());
            throw new PredictionUnavailableException("We couldn't reach the AI service or read its response. Please try again shortly.");
        }
    }

    public static class PredictionUnavailableException extends RuntimeException {
        PredictionUnavailableException(String message) {
            super(message);
        }
    }
}
