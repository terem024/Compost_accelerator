package com.group11.compostsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static com.group11.compostsystem.service.GeminiPredictionClient.PredictionUnavailableException;

@Component
public class FallbackPredictionClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(FallbackPredictionClient.class);
    private final GeminiPredictionClient gemini;
    private final GroqPredictionClient groq;
    private final ObjectMapper mapper;
    private final boolean fallbackEnabled;

    public FallbackPredictionClient(GeminiPredictionClient gemini, GroqPredictionClient groq,
            ObjectMapper mapper, @Value("${ai.fallback.enabled:true}") boolean fallbackEnabled) {
        this.gemini = gemini;
        this.groq = groq;
        this.mapper = mapper;
        this.fallbackEnabled = fallbackEnabled;
    }

    public record Result(String provider, String model, String raw, JsonNode json) { }

    public Result generate(String prompt) {
        try {
            String raw = gemini.generate(prompt);
            return new Result("Gemini", gemini.modelName(), raw, PredictionPayload.parse(mapper, raw));
        } catch (PredictionUnavailableException | IOException primaryFailure) {
            // Cancellation must not start another external request.
            if (Thread.currentThread().isInterrupted()) throw new PredictionUnavailableException("The prediction was interrupted. Please try again.");
            if (!fallbackEnabled || !groq.isConfigured()) {
                LOGGER.warn("Primary AI failed; backup is disabled or unconfigured. Set GROQ_API_KEY on Backend to enable failover.");
                if (primaryFailure instanceof PredictionUnavailableException unavailable) throw unavailable;
                throw new PredictionUnavailableException("The AI returned an unusable prediction. Please try again. Your daily prediction has not been used.");
            }
            LOGGER.warn("Primary AI unavailable or returned an unusable response; trying Groq backup.");
        }
        try {
            String raw = groq.generate(prompt);
            Result result = new Result("Groq", groq.modelName(), raw, PredictionPayload.parse(mapper, raw));
            LOGGER.info("AI prediction completed using Groq backup, model={}", result.model());
            return result;
        } catch (PredictionUnavailableException | IOException backupFailure) {
            LOGGER.warn("Both AI providers failed to produce a usable prediction.");
            throw new PredictionUnavailableException("Both AI services are currently unavailable. Please try again later. Your daily prediction has not been used.");
        }
    }
}
