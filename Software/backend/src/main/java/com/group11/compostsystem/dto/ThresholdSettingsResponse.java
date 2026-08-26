package com.group11.compostsystem.dto;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class ThresholdSettingsResponse {

    private BigDecimal moistureMin;
    private BigDecimal gasMax;
    private Integer readingIntervalSeconds;
    private Integer sprayDurationSeconds;
    private Integer fanDurationSeconds;
    private Integer sprayCooldownSeconds;
    private Integer fanCooldownSeconds;
    private Integer updatedBy;
    private Timestamp updatedAt;

    public ThresholdSettingsResponse(BigDecimal moistureMin, BigDecimal gasMax, Integer updatedBy, Timestamp updatedAt) {
        this(moistureMin, gasMax, 60, 15, 5, 30, 30, updatedBy, updatedAt);
    }

    public ThresholdSettingsResponse(BigDecimal moistureMin, BigDecimal gasMax,
                                     Integer readingIntervalSeconds,
                                     Integer sprayDurationSeconds,
                                     Integer fanDurationSeconds,
                                     Integer sprayCooldownSeconds,
                                     Integer fanCooldownSeconds,
                                     Integer updatedBy,
                                     Timestamp updatedAt) {
        this.moistureMin = moistureMin;
        this.gasMax = gasMax;
        this.readingIntervalSeconds = readingIntervalSeconds;
        this.sprayDurationSeconds = sprayDurationSeconds;
        this.fanDurationSeconds = fanDurationSeconds;
        this.sprayCooldownSeconds = sprayCooldownSeconds;
        this.fanCooldownSeconds = fanCooldownSeconds;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public BigDecimal getMoistureMin() {
        return moistureMin;
    }

    public BigDecimal getGasMax() {
        return gasMax;
    }

    public Integer getReadingIntervalSeconds() {
        return readingIntervalSeconds;
    }

    public Integer getSprayDurationSeconds() {
        return sprayDurationSeconds;
    }

    public Integer getFanDurationSeconds() {
        return fanDurationSeconds;
    }

    public Integer getSprayCooldownSeconds() {
        return sprayCooldownSeconds;
    }

    public Integer getFanCooldownSeconds() {
        return fanCooldownSeconds;
    }

    public Integer getUpdatedBy() {
        return updatedBy;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }
}
