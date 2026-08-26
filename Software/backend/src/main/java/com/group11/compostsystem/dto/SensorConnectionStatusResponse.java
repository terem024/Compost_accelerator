package com.group11.compostsystem.dto;

import java.sql.Timestamp;

public class SensorConnectionStatusResponse {

    private final String connectionStatus;
    private final String sensorStatus;
    private final Timestamp lastReadingAt;
    private final Timestamp disconnectedAt;
    private final int timeoutSeconds;

    public SensorConnectionStatusResponse(String connectionStatus,
                                          String sensorStatus,
                                          Timestamp lastReadingAt,
                                          Timestamp disconnectedAt,
                                          int timeoutSeconds) {
        this.connectionStatus = connectionStatus;
        this.sensorStatus = sensorStatus;
        this.lastReadingAt = lastReadingAt;
        this.disconnectedAt = disconnectedAt;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getConnectionStatus() {
        return connectionStatus;
    }

    public String getSensorStatus() {
        return sensorStatus;
    }

    public Timestamp getLastReadingAt() {
        return lastReadingAt;
    }

    public Timestamp getDisconnectedAt() {
        return disconnectedAt;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
