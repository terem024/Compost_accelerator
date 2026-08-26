package com.group11.compostsystem.dto;

import java.time.OffsetDateTime;

public class AIPredictionAvailabilityResponse {

    private final boolean canGenerate;
    private final String message;
    private final OffsetDateTime nextPredictionAt;
    private final AIPredictionResponse prediction;

    public AIPredictionAvailabilityResponse(
            boolean canGenerate,
            String message,
            OffsetDateTime nextPredictionAt,
            AIPredictionResponse prediction
    ) {
        this.canGenerate = canGenerate;
        this.message = message;
        this.nextPredictionAt = nextPredictionAt;
        this.prediction = prediction;
    }

    public boolean isCanGenerate() {
        return canGenerate;
    }

    public String getMessage() {
        return message;
    }

    public OffsetDateTime getNextPredictionAt() {
        return nextPredictionAt;
    }

    public AIPredictionResponse getPrediction() {
        return prediction;
    }
}
