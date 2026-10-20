package com.group11.compostsystem.service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.group11.compostsystem.dto.ActuatorActionResponse;
import com.group11.compostsystem.dto.SensorReadingRequest;
import com.group11.compostsystem.dto.SensorReadingResponse;
import com.group11.compostsystem.dto.ThresholdSettingsResponse;
import com.group11.compostsystem.dto.MoistureProbeDiagnostic;
import jakarta.annotation.PostConstruct;

@Service
public class SensorReadingService {

    private static final BigDecimal DEFAULT_MOISTURE_HIGH = new BigDecimal("70.00");
    private static final BigDecimal DEFAULT_GAS_LOW = new BigDecimal("40.00");
    private static final BigDecimal DEFAULT_TEMPERATURE_LOW = new BigDecimal("30.00");
    private static final BigDecimal DEFAULT_TEMPERATURE_HIGH = new BigDecimal("50.00");
    private static final BigDecimal DEFAULT_HUMIDITY_LOW = new BigDecimal("70.00");
    private static final BigDecimal DEFAULT_HUMIDITY_HIGH = new BigDecimal("85.00");

    private final JdbcTemplate jdbcTemplate;
    private final ThresholdService thresholdService;
    private final ActuatorLogService actuatorLogService;
    private final SensorSseService sensorSseService;
    private final SensorConnectionService sensorConnectionService;

    @PostConstruct
    public void ensureMoistureDiagnosticsTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS moisture_probe_diagnostics (
                    reading_id BIGINT NOT NULL,
                    probe INT NOT NULL,
                    gpio INT NOT NULL,
                    raw_adc INT NULL,
                    percent DECIMAL(6,2) NULL,
                    enabled BOOLEAN NOT NULL,
                    valid BOOLEAN NOT NULL,
                    PRIMARY KEY (reading_id, probe)
                )
                """);
    }

    private void attachMoistureDiagnostics(SensorReadingResponse response) {
        if (response == null) return;
        response.setMoistureProbes(jdbcTemplate.query("""
                SELECT probe, gpio, raw_adc, percent, enabled, valid
                FROM moisture_probe_diagnostics WHERE reading_id = ? ORDER BY probe
                """, (rs, row) -> new MoistureProbeDiagnostic(rs.getInt("probe"), rs.getInt("gpio"),
                rs.getObject("raw_adc", Integer.class), rs.getBigDecimal("percent"),
                rs.getBoolean("enabled"), rs.getBoolean("valid")), response.getReadingId()));
    }

    public SensorReadingService(JdbcTemplate jdbcTemplate,
                                ThresholdService thresholdService,
                                ActuatorLogService actuatorLogService,
                                SensorSseService sensorSseService,
                                SensorConnectionService sensorConnectionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.thresholdService = thresholdService;
        this.actuatorLogService = actuatorLogService;
        this.sensorSseService = sensorSseService;
        this.sensorConnectionService = sensorConnectionService;
    }

    public SensorReadingResponse saveSensorReading(SensorReadingRequest request) {
        validateMoistureDiagnostics(request.getMoistureProbes());
        BigDecimal moistureLevel = request.getEffectiveMoistureLevel();
        ThresholdSettingsResponse threshold = thresholdService.getThresholdSettings();
        Integer batchId = resolveBatchId(request.getBatchId());
        validateSensorValues(moistureLevel, request.getGasLevel(), request.getTemperatureC(), request.getHumidityLevel());

        String moistureStatus = statusFor(moistureLevel, threshold.getMoistureMin(), DEFAULT_MOISTURE_HIGH);
        String gasStatus = statusFor(request.getGasLevel(), DEFAULT_GAS_LOW, threshold.getGasMax());
        String temperatureStatus = statusFor(request.getTemperatureC(), DEFAULT_TEMPERATURE_LOW, DEFAULT_TEMPERATURE_HIGH);
        String humidityStatus = statusFor(request.getHumidityLevel(), DEFAULT_HUMIDITY_LOW, DEFAULT_HUMIDITY_HIGH);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO sensor_readings
                        (batch_id, moisture_level, gas_level, temperature_c, humidity_level,
                         moisture_status, gas_status, temperature_status, humidity_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setObject(1, batchId);
            ps.setObject(2, moistureLevel);
            ps.setObject(3, request.getGasLevel());
            ps.setObject(4, request.getTemperatureC());
            ps.setObject(5, request.getHumidityLevel());
            ps.setString(6, moistureStatus);
            ps.setString(7, gasStatus);
            ps.setString(8, temperatureStatus);
            ps.setString(9, humidityStatus);
            return ps;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("Sensor reading was saved, but no reading id was returned.");
        }

        SensorReadingResponse response = getSensorReadingById(generatedId.longValue());
        if (request.getMoistureProbes() != null) {
            for (MoistureProbeDiagnostic probe : request.getMoistureProbes()) {
                jdbcTemplate.update("""
                        INSERT INTO moisture_probe_diagnostics
                        (reading_id, probe, gpio, raw_adc, percent, enabled, valid)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, generatedId.longValue(), probe.probe(), probe.gpio(), probe.rawAdc(),
                        probe.percent(), probe.enabled(), probe.valid());
            }
        }
        attachMoistureDiagnostics(response);
        sensorConnectionService.recordReadingReceived(response);
        List<ActuatorActionResponse> actions = actuatorLogService.applyAutomaticControl(response, threshold);
        response.setActuatorActions(actions);

        // Publish the new reading to any SSE subscribers
        try {
            sensorSseService.publish(response);
        } catch (Exception ex) {
            // swallow - publishing to SSE should not break saving
        }

        return response;
    }

    private Integer resolveBatchId(Integer requestedBatchId) {
        if (requestedBatchId != null) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM compost_batches WHERE batch_id = ?",
                    Integer.class,
                    requestedBatchId
            );

            if (exists == null || exists == 0) {
                throw new IllegalArgumentException("Selected compost batch was not found.");
            }

            return requestedBatchId;
        }

        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT batch_id
                    FROM compost_batches
                    WHERE status = 'ACTIVE'
                    ORDER BY start_date DESC, batch_id DESC
                    LIMIT 1
                    """,
                    Integer.class
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new NoActiveBatchException(
                    "No active compost batch found. Create or activate a compost batch before saving sensor readings."
            );
        }
    }

    void validateMoistureDiagnostics(List<MoistureProbeDiagnostic> probes) {
        if (probes == null) return;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (MoistureProbeDiagnostic probe : probes) {
            if (probe == null || probe.probe() < 1 || probe.probe() > 2 || !seen.add(probe.probe())
                    || probe.gpio() < 0 || probe.gpio() > 39
                    || (probe.rawAdc() != null && (probe.rawAdc() < 0 || probe.rawAdc() > 4095))
                    || (probe.percent() != null && (probe.percent().signum() < 0
                        || probe.percent().compareTo(new BigDecimal("100")) > 0))
                    || (probe.valid() && (!probe.enabled() || probe.rawAdc() == null || probe.percent() == null))) {
                throw new IllegalArgumentException("Invalid moisture probe diagnostics.");
            }
        }
    }

    void validateSensorValues(BigDecimal moistureLevel,
                              BigDecimal gasLevel,
                              BigDecimal temperatureC,
                              BigDecimal humidityLevel) {
        if (moistureLevel == null && gasLevel == null && temperatureC == null && humidityLevel == null) {
            throw new IllegalArgumentException("At least one sensor value is required.");
        }

        if (gasLevel != null && (gasLevel.compareTo(BigDecimal.ZERO) < 0
                || gasLevel.compareTo(new BigDecimal("100.00")) > 0)) {
            throw new IllegalArgumentException("Gas level must be a percentage between 0 and 100.");
        }
    }

    String statusFor(BigDecimal value, BigDecimal lowThreshold, BigDecimal highThreshold) {
        if (value == null) {
            return null;
        }

        if (value.compareTo(lowThreshold) < 0) {
            return "LOW";
        }

        if (value.compareTo(highThreshold) > 0) {
            return "HIGH";
        }

        return "NORMAL";
    }

    private SensorReadingResponse getSensorReadingById(Long readingId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT reading_id, batch_id, moisture_level, gas_level, temperature_c, humidity_level,
                       moisture_status, gas_status, temperature_status, humidity_status, created_at
                FROM sensor_readings
                WHERE reading_id = ?
                """,
                (rs, rowNum) -> mapSensorReading(
                        rs.getLong("reading_id"),
                        rs.getObject("batch_id", Integer.class),
                        rs.getBigDecimal("moisture_level"),
                        rs.getBigDecimal("gas_level"),
                        rs.getBigDecimal("temperature_c"),
                        rs.getBigDecimal("humidity_level"),
                        rs.getString("moisture_status"),
                        rs.getString("gas_status"),
                        rs.getString("temperature_status"),
                        rs.getString("humidity_status"),
                        rs.getTimestamp("created_at")
                ),
                readingId
        );
    }

    public SensorReadingResponse getLatestSensorReading() {

        try {

            SensorReadingResponse response = jdbcTemplate.queryForObject("CALL sp_get_latest_sensor_reading()", (rs, rowNum) ->
                    mapSensorReading(
                            rs.getLong("reading_id"),
                            rs.getObject("batch_id", Integer.class),
                            rs.getBigDecimal("moisture_level"),
                            rs.getBigDecimal("gas_level"),
                            rs.getBigDecimal("temperature_c"),
                            rs.getBigDecimal("humidity_level"),
                            rs.getString("moisture_status"),
                            rs.getString("gas_status"),
                            rs.getString("temperature_status"),
                            rs.getString("humidity_status"),
                            rs.getTimestamp("created_at")
                    )
            );

            attachMoistureDiagnostics(response);
            return response;
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public List<SensorReadingResponse> getAllSensorReadings() {

        return jdbcTemplate.query("CALL sp_get_sensor_reading_history()", (rs, rowNum) ->
                mapSensorReading(
                        rs.getLong("reading_id"),
                        rs.getObject("batch_id", Integer.class),
                        rs.getBigDecimal("moisture_level"),
                        rs.getBigDecimal("gas_level"),
                        rs.getBigDecimal("temperature_c"),
                        rs.getBigDecimal("humidity_level"),
                        rs.getString("moisture_status"),
                        rs.getString("gas_status"),
                        rs.getString("temperature_status"),
                        rs.getString("humidity_status"),
                        rs.getTimestamp("created_at")
                )
        );
    }

    private SensorReadingResponse mapSensorReading(Long readingId,
                                                   Integer batchId,
                                                   BigDecimal moistureLevel,
                                                   BigDecimal gasLevel,
                                                   BigDecimal temperatureC,
                                                   BigDecimal humidityLevel,
                                                   String moistureStatus,
                                                   String gasStatus,
                                                   String temperatureStatus,
                                                   String humidityStatus,
                                                   Timestamp createdAt) {
        return new SensorReadingResponse(
                readingId,
                batchId,
                moistureLevel,
                gasLevel,
                temperatureC,
                humidityLevel,
                moistureStatus,
                gasStatus,
                temperatureStatus,
                humidityStatus,
                createdAt
        );
    }
}
