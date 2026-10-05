package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.ActuatorActionResponse;
import com.group11.compostsystem.dto.ActuatorLogResponse;
import com.group11.compostsystem.dto.ActuatorRuntimeStatusResponse;
import com.group11.compostsystem.dto.ActuatorStatusResponse;
import com.group11.compostsystem.dto.SensorReadingResponse;
import com.group11.compostsystem.dto.ThresholdSettingsResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Service
public class ActuatorLogService {

    private static final String FAN = "FAN";
    private static final String WATER_SPRAY = "WATER_SPRAY";
    private static final String STATUS_ON = "ON";
    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;

    public ActuatorLogService(JdbcTemplate jdbcTemplate, EmailService emailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailService = emailService;
    }

    public List<ActuatorActionResponse> applyAutomaticControl(SensorReadingResponse reading,
                                                              ThresholdSettingsResponse thresholds) {
        List<ActuatorActionResponse> actions = new ArrayList<>();

        if (reading == null || reading.getReadingId() == null || thresholds == null) {
            return actions;
        }

        if (reading.getMoistureLevel() != null
                && reading.getMoistureLevel().compareTo(thresholds.getMoistureMin()) < 0) {
            ActuatorActionResponse action = pulseActuator(
                    reading,
                    WATER_SPRAY,
                    "MOISTURE",
                    reading.getMoistureLevel(),
                    thresholds.getMoistureMin(),
                    valueOrDefault(thresholds.getSprayDurationSeconds(), 15),
                    valueOrDefault(thresholds.getSprayCooldownSeconds(), 30)
            );

            if (action != null) {
                actions.add(action);
            }
        }

        if (reading.getGasLevel() != null
                && reading.getGasLevel().compareTo(thresholds.getGasMax()) > 0) {
            ActuatorActionResponse action = pulseActuator(
                    reading,
                    FAN,
                    "GAS",
                    reading.getGasLevel(),
                    thresholds.getGasMax(),
                    valueOrDefault(thresholds.getFanDurationSeconds(), 5),
                    valueOrDefault(thresholds.getFanCooldownSeconds(), 30)
            );

            if (action != null) {
                actions.add(action);
            }
        }

        return actions;
    }

    public ActuatorStatusResponse getLatestActuatorStatus() {
        List<ActuatorRuntimeStatusResponse> runtimeStatuses = getRuntimeStatuses();
        boolean fanActive = isActive(runtimeStatuses, FAN);
        boolean waterSprayActive = isActive(runtimeStatuses, WATER_SPRAY);
        ActuatorLogResponse latestActivity = getLatestActivity();

        return new ActuatorStatusResponse(fanActive, waterSprayActive, runtimeStatuses, latestActivity);
    }

    public List<ActuatorRuntimeStatusResponse> getRuntimeStatuses() {
        return jdbcTemplate.query("CALL sp_get_actuator_runtime_status()", (rs, rowNum) ->
                new ActuatorRuntimeStatusResponse(
                        rs.getString("actuator_type"),
                        rs.getString("current_status"),
                        rs.getTimestamp("last_activated_at"),
                        rs.getTimestamp("cooldown_until"),
                        rs.getInt("last_duration_seconds"),
                        rs.getTimestamp("updated_at")
                )
        );
    }

    public List<ActuatorLogResponse> getActuatorLogHistory() {
        return jdbcTemplate.query("CALL sp_get_actuator_log_history()", (rs, rowNum) ->
                mapActuatorLog(rs.getLong("log_id"),
                        rs.getObject("batch_id", Integer.class),
                        rs.getObject("reading_id", Long.class),
                        rs.getString("actuator_type"),
                        rs.getString("status"),
                        rs.getString("trigger_source"),
                        rs.getBigDecimal("trigger_value"),
                        rs.getBigDecimal("threshold_value"),
                        rs.getObject("duration_seconds", Integer.class),
                        rs.getTimestamp("started_at"),
                        rs.getTimestamp("ended_at"),
                        rs.getTimestamp("created_at"))
        );
    }

    private ActuatorActionResponse pulseActuator(SensorReadingResponse reading,
                                                 String actuatorType,
                                                 String triggerSource,
                                                 BigDecimal triggerValue,
                                                 BigDecimal thresholdValue,
                                                 int durationSeconds,
                                                 int cooldownSeconds) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ActuatorRuntimeStatusResponse currentStatus = findRuntimeStatus(actuatorType);

        if (currentStatus != null
                && currentStatus.getCooldownUntil() != null
                && currentStatus.getCooldownUntil().after(now)) {
            return null;
        }

        Timestamp endedAt = new Timestamp(now.getTime() + (durationSeconds * 1000L));
        Timestamp cooldownUntil = new Timestamp(endedAt.getTime() + (cooldownSeconds * 1000L));

        ActuatorLogResponse log = jdbcTemplate.queryForObject(
                "CALL sp_insert_actuator_log(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (rs, rowNum) -> mapActuatorLog(
                        rs.getLong("log_id"),
                        rs.getObject("batch_id", Integer.class),
                        rs.getObject("reading_id", Long.class),
                        rs.getString("actuator_type"),
                        rs.getString("status"),
                        rs.getString("trigger_source"),
                        rs.getBigDecimal("trigger_value"),
                        rs.getBigDecimal("threshold_value"),
                        rs.getObject("duration_seconds", Integer.class),
                        rs.getTimestamp("started_at"),
                        rs.getTimestamp("ended_at"),
                        rs.getTimestamp("created_at")
                ),
                reading.getBatchId(),
                reading.getReadingId(),
                actuatorType,
                STATUS_ON,
                triggerSource,
                triggerValue,
                thresholdValue,
                durationSeconds,
                now,
                endedAt
        );

        jdbcTemplate.query(
                "CALL sp_update_actuator_runtime_status(?, ?, ?, ?, ?)",
                rs -> null,
                actuatorType,
                STATUS_ON,
                now,
                cooldownUntil,
                durationSeconds
        );

        // Send email notification
        String sensorReadings = formatSensorReadings(reading);
        emailService.sendActuatorActivationEmail(
                actuatorType,
                STATUS_ON,
                now.toString(),
                sensorReadings
        );

        return new ActuatorActionResponse(
                log != null ? log.getLogId() : null,
                actuatorType,
                STATUS_ON,
                triggerSource,
                triggerValue,
                thresholdValue,
                durationSeconds,
                now,
                endedAt,
                cooldownUntil
        );
    }

    private ActuatorRuntimeStatusResponse findRuntimeStatus(String actuatorType) {
        return getRuntimeStatuses().stream()
                .filter(status -> actuatorType.equals(status.getActuatorType()))
                .findFirst()
                .orElse(null);
    }

    private String formatSensorReadings(SensorReadingResponse reading) {
        StringBuilder sb = new StringBuilder();
        if (reading.getMoistureLevel() != null) {
            sb.append("Moisture: ").append(reading.getMoistureLevel()).append("%\n");
        }
        if (reading.getGasLevel() != null) {
            sb.append("Gas: ").append(reading.getGasLevel()).append("%\n");
        }
        if (reading.getTemperatureC() != null) {
            sb.append("Temperature: ").append(reading.getTemperatureC()).append("°C\n");
        }
        if (reading.getHumidityLevel() != null) {
            sb.append("Humidity: ").append(reading.getHumidityLevel()).append("%\n");
        }
        return sb.toString().trim();
    }

    private ActuatorLogResponse getLatestActivity() {
        try {
            return jdbcTemplate.queryForObject("CALL sp_get_latest_actuator_status()", (rs, rowNum) ->
                    mapActuatorLog(rs.getLong("log_id"),
                            rs.getObject("batch_id", Integer.class),
                            rs.getObject("reading_id", Long.class),
                            rs.getString("actuator_type"),
                            rs.getString("status"),
                            rs.getString("trigger_source"),
                            rs.getBigDecimal("trigger_value"),
                            rs.getBigDecimal("threshold_value"),
                            rs.getObject("duration_seconds", Integer.class),
                            rs.getTimestamp("started_at"),
                            rs.getTimestamp("ended_at"),
                            rs.getTimestamp("created_at"))
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private ActuatorLogResponse mapActuatorLog(Long logId,
                                               Integer batchId,
                                               Long readingId,
                                               String actuatorType,
                                               String status,
                                               String triggerSource,
                                               BigDecimal triggerValue,
                                               BigDecimal thresholdValue,
                                               Integer durationSeconds,
                                               Timestamp startedAt,
                                               Timestamp endedAt,
                                               Timestamp createdAt) {
        return new ActuatorLogResponse(
                logId,
                batchId,
                readingId,
                actuatorType,
                status,
                triggerSource,
                triggerValue,
                thresholdValue,
                durationSeconds,
                startedAt,
                endedAt,
                createdAt
        );
    }

    private int valueOrDefault(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private boolean isActive(List<ActuatorRuntimeStatusResponse> runtimeStatuses, String actuatorType) {
        return runtimeStatuses.stream()
                .filter(status -> actuatorType.equals(status.getActuatorType()))
                .anyMatch(status -> STATUS_ON.equals(status.getCurrentStatus()));
    }
}
