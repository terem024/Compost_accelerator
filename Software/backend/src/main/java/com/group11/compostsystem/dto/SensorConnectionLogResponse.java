package com.group11.compostsystem.dto;

import java.sql.Timestamp;

public class SensorConnectionLogResponse {

    private final Long logId;
    private final String eventType;
    private final String sensorStatus;
    private final Timestamp lastReadingAt;
    private final Timestamp occurredAt;

    public SensorConnectionLogResponse(Long logId,
                                       String eventType,
                                       String sensorStatus,
                                       Timestamp lastReadingAt,
                                       Timestamp occurredAt) {
        this.logId = logId;
        this.eventType = eventType;
        this.sensorStatus = sensorStatus;
        this.lastReadingAt = lastReadingAt;
        this.occurredAt = occurredAt;
    }

    public Long getLogId() {
        return logId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSensorStatus() {
        return sensorStatus;
    }

    public Timestamp getLastReadingAt() {
        return lastReadingAt;
    }

    public Timestamp getOccurredAt() {
        return occurredAt;
    }
}
