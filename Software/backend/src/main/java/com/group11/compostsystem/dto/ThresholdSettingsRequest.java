package com.group11.compostsystem.dto;

import java.math.BigDecimal;

public class ThresholdSettingsRequest {

    private BigDecimal moistureMin;
    private BigDecimal gasMax;
    private Integer readingIntervalSeconds;
    private Integer sprayDurationSeconds;
    private Integer fanDurationSeconds;
    private Integer sprayCooldownSeconds;
    private Integer fanCooldownSeconds;
    private String currentPassword;

    public BigDecimal getMoistureMin() {
        return moistureMin;
    }

    public void setMoistureMin(BigDecimal moistureMin) {
        this.moistureMin = moistureMin;
    }

    public BigDecimal getGasMax() {
        return gasMax;
    }

    public void setGasMax(BigDecimal gasMax) {
        this.gasMax = gasMax;
    }

    public Integer getReadingIntervalSeconds() {
        return readingIntervalSeconds;
    }

    public void setReadingIntervalSeconds(Integer readingIntervalSeconds) {
        this.readingIntervalSeconds = readingIntervalSeconds;
    }

    public Integer getSprayDurationSeconds() {
        return sprayDurationSeconds;
    }

    public void setSprayDurationSeconds(Integer sprayDurationSeconds) {
        this.sprayDurationSeconds = sprayDurationSeconds;
    }

    public Integer getFanDurationSeconds() {
        return fanDurationSeconds;
    }

    public void setFanDurationSeconds(Integer fanDurationSeconds) {
        this.fanDurationSeconds = fanDurationSeconds;
    }

    public Integer getSprayCooldownSeconds() {
        return sprayCooldownSeconds;
    }

    public void setSprayCooldownSeconds(Integer sprayCooldownSeconds) {
        this.sprayCooldownSeconds = sprayCooldownSeconds;
    }

    public Integer getFanCooldownSeconds() {
        return fanCooldownSeconds;
    }

    public void setFanCooldownSeconds(Integer fanCooldownSeconds) {
        this.fanCooldownSeconds = fanCooldownSeconds;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }
}
