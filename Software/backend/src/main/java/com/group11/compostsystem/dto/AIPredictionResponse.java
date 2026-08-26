package com.group11.compostsystem.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public class AIPredictionResponse {

    private boolean success;
    private String message;

    private Integer predictionId;
    private Integer batchId;
    private String predictedCondition;
    private String predictionSummary;
    private LocalDate estimatedReadyDate;
    private Integer estimatedDaysRemaining;
    private String recommendation;
    private String trendSummary;
    private BigDecimal confidenceScore;
    private boolean dailyLimitReached;
    private OffsetDateTime predictionCreatedAt;
    private OffsetDateTime nextPredictionAt;

    public AIPredictionResponse() {
    }

    public static AIPredictionResponse success(
            String message,
            Integer predictionId,
            Integer batchId,
            String predictedCondition,
            String predictionSummary,
            LocalDate estimatedReadyDate,
            Integer estimatedDaysRemaining,
            String recommendation,
            String trendSummary,
            BigDecimal confidenceScore
    ) {
        AIPredictionResponse response = new AIPredictionResponse();
        response.setSuccess(true);
        response.setMessage(message);
        response.setPredictionId(predictionId);
        response.setBatchId(batchId);
        response.setPredictedCondition(predictedCondition);
        response.setPredictionSummary(predictionSummary);
        response.setEstimatedReadyDate(estimatedReadyDate);
        response.setEstimatedDaysRemaining(estimatedDaysRemaining);
        response.setRecommendation(recommendation);
        response.setTrendSummary(trendSummary);
        response.setConfidenceScore(confidenceScore);
        return response;
    }

    public static AIPredictionResponse failed(String message) {
        AIPredictionResponse response = new AIPredictionResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getPredictionId() {
        return predictionId;
    }

    public void setPredictionId(Integer predictionId) {
        this.predictionId = predictionId;
    }

    public Integer getBatchId() {
        return batchId;
    }

    public void setBatchId(Integer batchId) {
        this.batchId = batchId;
    }

    public String getPredictedCondition() {
        return predictedCondition;
    }

    public void setPredictedCondition(String predictedCondition) {
        this.predictedCondition = predictedCondition;
    }

    public String getPredictionSummary() {
        return predictionSummary;
    }

    public void setPredictionSummary(String predictionSummary) {
        this.predictionSummary = predictionSummary;
    }

    public LocalDate getEstimatedReadyDate() {
        return estimatedReadyDate;
    }

    public void setEstimatedReadyDate(LocalDate estimatedReadyDate) {
        this.estimatedReadyDate = estimatedReadyDate;
    }

    public Integer getEstimatedDaysRemaining() {
        return estimatedDaysRemaining;
    }

    public void setEstimatedDaysRemaining(Integer estimatedDaysRemaining) {
        this.estimatedDaysRemaining = estimatedDaysRemaining;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public String getTrendSummary() {
        return trendSummary;
    }

    public void setTrendSummary(String trendSummary) {
        this.trendSummary = trendSummary;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public boolean isDailyLimitReached() {
        return dailyLimitReached;
    }

    public void setDailyLimitReached(boolean dailyLimitReached) {
        this.dailyLimitReached = dailyLimitReached;
    }

    public OffsetDateTime getPredictionCreatedAt() {
        return predictionCreatedAt;
    }

    public void setPredictionCreatedAt(OffsetDateTime predictionCreatedAt) {
        this.predictionCreatedAt = predictionCreatedAt;
    }

    public OffsetDateTime getNextPredictionAt() {
        return nextPredictionAt;
    }

    public void setNextPredictionAt(OffsetDateTime nextPredictionAt) {
        this.nextPredictionAt = nextPredictionAt;
    }
}
