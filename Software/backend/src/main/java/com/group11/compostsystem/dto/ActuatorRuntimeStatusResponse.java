package com.group11.compostsystem.dto;

import java.sql.Timestamp;

public class ActuatorRuntimeStatusResponse {

    private String actuatorType;
    private String currentStatus;
    private Timestamp lastActivatedAt;
    private Timestamp cooldownUntil;
    private Integer lastDurationSeconds;
    private Timestamp updatedAt;

    public ActuatorRuntimeStatusResponse(String actuatorType, String currentStatus,
                                         Timestamp lastActivatedAt, Timestamp cooldownUntil,
                                         Integer lastDurationSeconds, Timestamp updatedAt) {
        this.actuatorType = actuatorType;
        this.currentStatus = currentStatus;
        this.lastActivatedAt = lastActivatedAt;
        this.cooldownUntil = cooldownUntil;
        this.lastDurationSeconds = lastDurationSeconds;
        this.updatedAt = updatedAt;
    }

    public String getActuatorType() {
        return actuatorType;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public Timestamp getLastActivatedAt() {
        return lastActivatedAt;
    }

    public Timestamp getCooldownUntil() {
        return cooldownUntil;
    }

    public Integer getLastDurationSeconds() {
        return lastDurationSeconds;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }
}
