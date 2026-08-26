package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.SensorReadingRequest;
import com.group11.compostsystem.dto.SensorReadingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class ActuatorLogServiceTests {

    @Autowired
    private SensorReadingService sensorReadingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void saveSensorReadingLogsWaterSprayActivation() {
        jdbcTemplate.update(
                """
                UPDATE actuator_runtime_status
                SET current_status = 'OFF',
                    cooldown_until = NULL
                WHERE actuator_type = 'WATER_SPRAY'
                """
        );

        SensorReadingRequest request = new SensorReadingRequest();
        request.setBatchId(1);
        request.setMoistureLevel(new BigDecimal("45.00"));
        request.setGasLevel(new BigDecimal("45.00"));
        request.setTemperatureC(new BigDecimal("35.00"));
        request.setHumidityLevel(new BigDecimal("55.00"));

        SensorReadingResponse response = sensorReadingService.saveSensorReading(request);

        Integer logCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM actuator_logs
                WHERE related_reading_id = ?
                  AND batch_id = ?
                  AND actuator_type = 'WATER_SPRAY'
                  AND status = 'ON'
                  AND trigger_source = 'MOISTURE'
                  AND trigger_value = 45.00
                  AND threshold_value = 50.00
                  AND duration_seconds = (
                      SELECT spray_duration_seconds
                      FROM threshold_settings
                      ORDER BY setting_id DESC
                      LIMIT 1
                  )
                """,
                Integer.class,
                response.getReadingId(),
                response.getBatchId()
        );

        assertEquals(1, logCount);
    }
}
