package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.ActuatorActionResponse;
import com.group11.compostsystem.dto.ActuatorLogResponse;
import com.group11.compostsystem.dto.SensorReadingResponse;
import com.group11.compostsystem.dto.ThresholdSettingsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActuatorControlLogicTests {

    @Test
    @SuppressWarnings("unchecked")
    void lowMoistureRequestsFifteenSecondSprayWithThirtySecondCooldown() {
        ActuatorLogService service = serviceWithAvailableActuators();

        List<ActuatorActionResponse> actions = service.applyAutomaticControl(reading("40", "20"), thresholds());

        assertEquals(1, actions.size());
        ActuatorActionResponse action = actions.get(0);
        assertEquals("WATER_SPRAY", action.getActuatorType());
        assertEquals(15, action.getDurationSeconds());
        assertEquals(30_000L, action.getCooldownUntil().getTime() - action.getEndedAt().getTime());
    }

    @Test
    void highGasRequestsFiveSecondFanWithThirtySecondCooldown() {
        ActuatorLogService service = serviceWithAvailableActuators();

        List<ActuatorActionResponse> actions = service.applyAutomaticControl(reading("70", "70"), thresholds());

        assertEquals(1, actions.size());
        ActuatorActionResponse action = actions.get(0);
        assertEquals("FAN", action.getActuatorType());
        assertEquals(5, action.getDurationSeconds());
        assertEquals(30_000L, action.getCooldownUntil().getTime() - action.getEndedAt().getTime());
    }

    @Test
    void readingsExactlyAtThresholdDoNotActivateEitherActuator() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ActuatorLogService service = new ActuatorLogService(jdbc, mock(EmailService.class));

        assertTrue(service.applyAutomaticControl(reading("50", "60"), thresholds()).isEmpty());
        verifyNoInteractions(jdbc);
    }

    @SuppressWarnings("unchecked")
    private ActuatorLogService serviceWithAvailableActuators() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(startsWith("CALL sp_get_actuator_runtime_status"), any(RowMapper.class)))
                .thenReturn(List.of());
        when(jdbc.queryForObject(startsWith("CALL sp_insert_actuator_log"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(new ActuatorLogResponse(1L, 3, 10L, "ACTUATOR", "ON", "SENSOR",
                        BigDecimal.ZERO, BigDecimal.ZERO, 1, new Timestamp(0), new Timestamp(0), new Timestamp(0)));
        when(jdbc.query(startsWith("CALL sp_update_actuator_runtime_status"),
                any(ResultSetExtractor.class), any(Object[].class))).thenReturn(null);
        return new ActuatorLogService(jdbc, mock(EmailService.class));
    }

    private SensorReadingResponse reading(String moisture, String gas) {
        return new SensorReadingResponse(
                10L, 3, new BigDecimal(moisture), new BigDecimal(gas),
                new BigDecimal("30"), new BigDecimal("70"),
                "NORMAL", "NORMAL", "NORMAL", "NORMAL", new Timestamp(System.currentTimeMillis())
        );
    }

    private ThresholdSettingsResponse thresholds() {
        return new ThresholdSettingsResponse(
                new BigDecimal("50"), new BigDecimal("60"), 60,
                15, 5, 30, 30, 7, null
        );
    }
}
