package com.group11.compostsystem.dto;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class ActuatorActionResponse {

    private Long logId;
    private String actuatorType;
    private String status;
    private String triggerSource;
    private BigDecimal triggerValue;
    private BigDecimal thresholdValue;
    private Integer durationSeconds;
    private Timestamp startedAt;
    private Timestamp endedAt;
    private Timestamp cooldownUntil;

    public ActuatorActionResponse(Long logId, String actuatorType, String status, String triggerSource,
                                  BigDecimal triggerValue, BigDecimal thresholdValue,
                                  Integer durationSeconds, Timestamp startedAt,
                                  Timestamp endedAt, Timestamp cooldownUntil) {
        this.logId = logId;
        this.actuatorType = actuatorType;
        this.status = status;
        this.triggerSource = triggerSource;
        this.triggerValue = triggerValue;
        this.thresholdValue = thresholdValue;
        this.durationSeconds = durationSeconds;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.cooldownUntil = cooldownUntil;
    }

    public Long getLogId() {
        return logId;
    }

    public String getActuatorType() {
        return actuatorType;
    }

    public String getStatus() {
        return status;
    }

    public String getTriggerSource() {
        return triggerSource;
    }

    public BigDecimal getTriggerValue() {
        return triggerValue;
    }

    public BigDecimal getThresholdValue() {
        return thresholdValue;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public Timestamp getStartedAt() {
        return startedAt;
    }

    public Timestamp getEndedAt() {
        return endedAt;
    }

    public Timestamp getCooldownUntil() {
        return cooldownUntil;
    }
}
