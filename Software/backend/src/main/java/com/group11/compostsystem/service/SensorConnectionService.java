package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.SensorConnectionLogResponse;
import com.group11.compostsystem.dto.SensorConnectionStatusResponse;
import com.group11.compostsystem.dto.SensorReadingResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class SensorConnectionService {

    private static final String CONNECTED = "CONNECTED";
    private static final String DISCONNECTED = "DISCONNECTED";
    private static final String RECONNECTED = "RECONNECTED";
    private static final String WAITING = "WAITING";
    private static final String SENSOR_STATUS_NA = "NA";

    private final JdbcTemplate jdbcTemplate;
    private final SensorSseService sensorSseService;
    private final int timeoutSeconds;
    private final Instant applicationStartedAt = Instant.now();

    private boolean initialized;
    private boolean disconnected;
    private Timestamp lastReadingAt;
    private Timestamp disconnectedAt;

    public SensorConnectionService(JdbcTemplate jdbcTemplate,
                                   SensorSseService sensorSseService,
                                   @Value("${app.sensor.connection-timeout-seconds:180}") int timeoutSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.sensorSseService = sensorSseService;
        this.timeoutSeconds = timeoutSeconds;
    }

    @PostConstruct
    public synchronized void initialize() {
        lastReadingAt = queryLatestReadingAt();
        SensorConnectionLogResponse latestEvent = queryLatestConnectionEvent();
        disconnected = latestEvent != null
                && DISCONNECTED.equals(latestEvent.getEventType())
                && (lastReadingAt == null || !lastReadingAt.after(latestEvent.getOccurredAt()));
        disconnectedAt = disconnected ? latestEvent.getOccurredAt() : null;
        initialized = true;
    }

    @Scheduled(fixedDelayString = "${app.sensor.connection-check-milliseconds:5000}")
    public synchronized void checkForTimeout() {
        ensureInitialized();
        Timestamp latestDatabaseReading = queryLatestReadingAt();
        if (latestDatabaseReading != null
                && (lastReadingAt == null || latestDatabaseReading.after(lastReadingAt))) {
            lastReadingAt = latestDatabaseReading;
        }

        if (!disconnected && isTimedOut(Instant.now())) {
            disconnectedAt = Timestamp.from(Instant.now());
            insertEvent(DISCONNECTED, lastReadingAt, disconnectedAt);
            disconnected = true;
            sensorSseService.publishConnectionStatus(buildStatus());
        }
    }

    public synchronized void recordReadingReceived(SensorReadingResponse reading) {
        ensureInitialized();
        lastReadingAt = reading != null && reading.getCreatedAt() != null
                ? reading.getCreatedAt()
                : Timestamp.from(Instant.now());

        if (disconnected) {
            Timestamp reconnectedAt = Timestamp.from(Instant.now());
            insertEvent(RECONNECTED, lastReadingAt, reconnectedAt);
            disconnected = false;
            disconnectedAt = null;
            sensorSseService.publishConnectionStatus(buildStatus());
        }
    }

    public synchronized SensorConnectionStatusResponse getStatus() {
        checkForTimeout();
        return buildStatus();
    }

    public List<SensorConnectionLogResponse> getHistory() {
        return jdbcTemplate.query(
                """
                SELECT log_id, event_type, sensor_status, last_reading_at, occurred_at
                FROM sensor_connection_logs
                ORDER BY occurred_at DESC, log_id DESC
                """,
                (rs, rowNum) -> new SensorConnectionLogResponse(
                        rs.getLong("log_id"),
                        rs.getString("event_type"),
                        rs.getString("sensor_status"),
                        rs.getTimestamp("last_reading_at"),
                        rs.getTimestamp("occurred_at")
                )
        );
    }

    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    private boolean isTimedOut(Instant now) {
        Instant reference = lastReadingAt != null
                ? lastReadingAt.toInstant()
                : applicationStartedAt;
        return !now.isBefore(reference.plusSeconds(timeoutSeconds));
    }

    private SensorConnectionStatusResponse buildStatus() {
        String connectionStatus;
        if (disconnected) {
            connectionStatus = DISCONNECTED;
        } else if (lastReadingAt == null) {
            connectionStatus = WAITING;
        } else {
            connectionStatus = CONNECTED;
        }

        return new SensorConnectionStatusResponse(
                connectionStatus,
                disconnected ? SENSOR_STATUS_NA : connectionStatus,
                lastReadingAt,
                disconnectedAt,
                timeoutSeconds
        );
    }

    private Timestamp queryLatestReadingAt() {
        return jdbcTemplate.queryForObject(
                "SELECT MAX(created_at) FROM sensor_readings",
                Timestamp.class
        );
    }

    private SensorConnectionLogResponse queryLatestConnectionEvent() {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT log_id, event_type, sensor_status, last_reading_at, occurred_at
                    FROM sensor_connection_logs
                    ORDER BY occurred_at DESC, log_id DESC
                    LIMIT 1
                    """,
                    (rs, rowNum) -> new SensorConnectionLogResponse(
                            rs.getLong("log_id"),
                            rs.getString("event_type"),
                            rs.getString("sensor_status"),
                            rs.getTimestamp("last_reading_at"),
                            rs.getTimestamp("occurred_at")
                    )
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private void insertEvent(String eventType, Timestamp latestReading, Timestamp occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO sensor_connection_logs
                    (event_type, sensor_status, last_reading_at, occurred_at)
                VALUES (?, ?, ?, ?)
                """,
                eventType,
                SENSOR_STATUS_NA,
                latestReading,
                occurredAt
        );
    }
}
