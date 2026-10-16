package com.group11.compostsystem.dto;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

public class DailyReportResponse {
    private Long reportId;
    private Integer batchId;
    private String batchCode;
    private String batchName;
    private String batchStatus;
    private LocalDate batchStartDate;
    private LocalDate reportDate;
    private Long readingCount;
    private BigDecimal averageMoisture;
    private BigDecimal minimumMoisture;
    private BigDecimal maximumMoisture;
    private BigDecimal averageGas;
    private BigDecimal minimumGas;
    private BigDecimal maximumGas;
    private BigDecimal averageTemperature;
    private BigDecimal minimumTemperature;
    private BigDecimal maximumTemperature;
    private BigDecimal averageHumidity;
    private BigDecimal minimumHumidity;
    private BigDecimal maximumHumidity;
    private String sensorAvailabilityIssues;
    private Timestamp generatedAt;

    public DailyReportResponse(Long reportId,
                               Integer batchId,
                               String batchCode,
                               String batchName,
                               String batchStatus,
                               LocalDate batchStartDate,
                               LocalDate reportDate,
                               Long readingCount,
                               BigDecimal averageMoisture,
                               BigDecimal minimumMoisture,
                               BigDecimal maximumMoisture,
                               BigDecimal averageGas,
                               BigDecimal minimumGas,
                               BigDecimal maximumGas,
                               BigDecimal averageTemperature,
                               BigDecimal minimumTemperature,
                               BigDecimal maximumTemperature,
                               BigDecimal averageHumidity,
                               BigDecimal minimumHumidity,
                               BigDecimal maximumHumidity,
                               String sensorAvailabilityIssues,
                               Timestamp generatedAt) {
        this.reportId = reportId;
        this.batchId = batchId;
        this.batchCode = batchCode;
        this.batchName = batchName;
        this.batchStatus = batchStatus;
        this.batchStartDate = batchStartDate;
        this.reportDate = reportDate;
        this.readingCount = readingCount;
        this.averageMoisture = averageMoisture;
        this.minimumMoisture = minimumMoisture;
        this.maximumMoisture = maximumMoisture;
        this.averageGas = averageGas;
        this.minimumGas = minimumGas;
        this.maximumGas = maximumGas;
        this.averageTemperature = averageTemperature;
        this.minimumTemperature = minimumTemperature;
        this.maximumTemperature = maximumTemperature;
        this.averageHumidity = averageHumidity;
        this.minimumHumidity = minimumHumidity;
        this.maximumHumidity = maximumHumidity;
        this.sensorAvailabilityIssues = sensorAvailabilityIssues;
        this.generatedAt = generatedAt;
    }

    public Long getReportId() {
        return reportId;
    }

    public LocalDate getReportDate() {
        return reportDate;
    }

    public Integer getBatchId() { return batchId; }

    public String getBatchCode() { return batchCode; }

    public String getBatchName() { return batchName; }

    public String getBatchStatus() { return batchStatus; }

    public LocalDate getBatchStartDate() { return batchStartDate; }

    public Long getReadingCount() {
        return readingCount;
    }

    public BigDecimal getAverageMoisture() {
        return averageMoisture;
    }

    public BigDecimal getMinimumMoisture() {
        return minimumMoisture;
    }

    public BigDecimal getMaximumMoisture() {
        return maximumMoisture;
    }

    public BigDecimal getAverageGas() {
        return averageGas;
    }

    public BigDecimal getMinimumGas() {
        return minimumGas;
    }

    public BigDecimal getMaximumGas() {
        return maximumGas;
    }

    public BigDecimal getAverageTemperature() {
        return averageTemperature;
    }

    public BigDecimal getMinimumTemperature() {
        return minimumTemperature;
    }

    public BigDecimal getMaximumTemperature() {
        return maximumTemperature;
    }

    public BigDecimal getAverageHumidity() {
        return averageHumidity;
    }

    public BigDecimal getMinimumHumidity() {
        return minimumHumidity;
    }

    public BigDecimal getMaximumHumidity() {
        return maximumHumidity;
    }

    public String getSensorAvailabilityIssues() {
        return sensorAvailabilityIssues;
    }

    public Timestamp getGeneratedAt() {
        return generatedAt;
    }
}
