package com.group11.compostsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PredictionPayload {
    private static final Set<String> CONDITIONS = Set.of("OPTIMAL", "TOO_DRY", "TOO_WET",
            "HIGH_GAS_LEVEL", "HIGH_TEMPERATURE", "LOW_TEMPERATURE", "HIGH_HUMIDITY",
            "LOW_HUMIDITY", "NEEDS_ATTENTION");
    private static final List<String> TEXT_FIELDS = List.of("predicted_condition", "prediction_summary",
            "recommendation", "trend_summary");

    private PredictionPayload() { }

    static Map<String, Object> schema() {
        Map<String, Object> fields = Map.of(
                "predicted_condition", Map.of("type", "string", "enum", CONDITIONS),
                "prediction_summary", Map.of("type", "string"),
                "recommendation", Map.of("type", "string"),
                "trend_summary", Map.of("type", "string"),
                "estimated_ready_date", Map.of("type", List.of("string", "null")),
                "estimated_days_remaining", Map.of("type", List.of("integer", "null")),
                "confidence_score", Map.of("type", "number", "minimum", 0, "maximum", 1));
        return Map.of("type", "object", "properties", fields, "required", fields.keySet(),
                "additionalProperties", false);
    }

    static JsonNode parse(ObjectMapper mapper, String raw) throws IOException {
        if (raw == null) throw new IOException("Missing prediction");
        String cleaned = raw.trim();
        if (cleaned.startsWith("```json") && cleaned.endsWith("```")) {
            cleaned = cleaned.substring(7, cleaned.length() - 3).trim();
        } else if (cleaned.startsWith("```") && cleaned.endsWith("```")) {
            cleaned = cleaned.substring(3, cleaned.length() - 3).trim();
        }
        JsonNode node = mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(cleaned);
        if (node == null || !node.isObject()) throw new IOException("Invalid prediction object");
        for (String field : TEXT_FIELDS) {
            if (!node.path(field).isTextual() || node.path(field).asText().isBlank()) {
                throw new IOException("Missing prediction text");
            }
        }
        if (!CONDITIONS.contains(node.path("predicted_condition").asText())) {
            throw new IOException("Unknown prediction condition");
        }
        JsonNode date = node.get("estimated_ready_date");
        JsonNode days = node.get("estimated_days_remaining");
        JsonNode confidence = node.path("confidence_score");
        if (date == null || days == null || (!date.isNull() && !date.isTextual())
                || (!days.isNull() && (!days.isIntegralNumber() || !days.canConvertToInt()))
                || !confidence.isNumber() || !Double.isFinite(confidence.asDouble())
                || confidence.asDouble() < 0 || confidence.asDouble() > 1) {
            throw new IOException("Invalid prediction field types");
        }
        return node;
    }
}
