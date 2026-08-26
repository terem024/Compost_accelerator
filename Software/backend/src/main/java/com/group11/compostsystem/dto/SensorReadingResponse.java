package com.group11.compostsystem.dto;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class SensorReadingResponse {

    private Long readingId;
    private Integer batchId;
    private BigDecimal moistureLevel;
    private BigDecimal gasLevel;
    private BigDecimal temperatureC;
    private BigDecimal humidityLevel;
    private String moistureStatus;
    private String gasStatus;
    private String temperatureStatus;
    private String humidityStatus;
    private Timestamp createdAt;
    private List<ActuatorActionResponse> actuatorActions = new ArrayList<>();

    public SensorReadingResponse(Long readingId, Integer batchId, BigDecimal moistureLevel, BigDecimal gasLevel,
                                 BigDecimal temperatureC, BigDecimal humidityLevel,
                                 String moistureStatus, String gasStatus,
                                 String temperatureStatus, String humidityStatus,
                                 Timestamp createdAt) {
        this.readingId = readingId;
        this.batchId = batchId;
        this.moistureLevel = moistureLevel;
        this.gasLevel = gasLevel;
        this.temperatureC = temperatureC;
        this.humidityLevel = humidityLevel;
        this.moistureStatus = moistureStatus;
        this.gasStatus = gasStatus;
        this.temperatureStatus = temperatureStatus;
        this.humidityStatus = humidityStatus;
        this.createdAt = createdAt;
    }

    public Long getReadingId() {
        return readingId;
    }

    public Integer getBatchId() {
        return batchId;
    }

    public BigDecimal getMoistureLevel() {
        return moistureLevel;
    }

    public BigDecimal getGasLevel() {
        return gasLevel;
    }

    public BigDecimal getTemperatureC() {
        return temperatureC;
    }

    public BigDecimal getHumidityLevel() {
        return humidityLevel;
    }

    public String getMoistureStatus() {
        return moistureStatus;
    }

    public String getGasStatus() {
        return gasStatus;
    }

    public String getTemperatureStatus() {
        return temperatureStatus;
    }

    public String getHumidityStatus() {
        return humidityStatus;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public List<ActuatorActionResponse> getActuatorActions() {
        return actuatorActions;
    }

    public void setActuatorActions(List<ActuatorActionResponse> actuatorActions) {
        this.actuatorActions = actuatorActions != null ? actuatorActions : new ArrayList<>();
    }
}
