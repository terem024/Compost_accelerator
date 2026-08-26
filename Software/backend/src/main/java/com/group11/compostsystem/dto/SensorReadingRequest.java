package com.group11.compostsystem.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class SensorReadingRequest {

    private Integer batchId;
    private BigDecimal moisturePercent1;
    private BigDecimal moisturePercent2;
    private BigDecimal moistureLevel;
    private BigDecimal gasLevel;
    private BigDecimal temperatureC;
    private BigDecimal humidityLevel;

    public Integer getBatchId() {
        return batchId;
    }

    public void setBatchId(Integer batchId) {
        this.batchId = batchId;
    }

    public BigDecimal getMoisturePercent1() {
        return moisturePercent1;
    }

    public void setMoisturePercent1(BigDecimal moisturePercent1) {
        this.moisturePercent1 = moisturePercent1;
    }

    public BigDecimal getMoisturePercent2() {
        return moisturePercent2;
    }

    public void setMoisturePercent2(BigDecimal moisturePercent2) {
        this.moisturePercent2 = moisturePercent2;
    }

    public BigDecimal getMoistureLevel() {
        return moistureLevel;
    }

    public void setMoistureLevel(BigDecimal moistureLevel) {
        this.moistureLevel = moistureLevel;
    }

    public BigDecimal getEffectiveMoistureLevel() {
        if (moistureLevel != null) {
            return moistureLevel;
        }

        if (moisturePercent1 != null && moisturePercent2 != null) {
            return moisturePercent1
                    .add(moisturePercent2)
                    .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        }

        if (moisturePercent1 != null) {
            return moisturePercent1;
        }

        return moisturePercent2;
    }

    public BigDecimal getGasLevel() {
        return gasLevel;
    }

    public void setGasLevel(BigDecimal gasLevel) {
        this.gasLevel = gasLevel;
    }

    public BigDecimal getTemperatureC() {
        return temperatureC;
    }

    public void setTemperatureC(BigDecimal temperatureC) {
        this.temperatureC = temperatureC;
    }

    public BigDecimal getHumidityLevel() {
        return humidityLevel;
    }

    public void setHumidityLevel(BigDecimal humidityLevel) {
        this.humidityLevel = humidityLevel;
    }
}
