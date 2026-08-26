package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.SensorConnectionLogResponse;
import com.group11.compostsystem.dto.SensorReadingResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensorConnectionServiceTests {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private SensorSseService sensorSseService;

    @Test
    void logsOneDisconnectAndOneReconnectPerOutage() {
        when(jdbcTemplate.queryForObject(
                "SELECT MAX(created_at) FROM sensor_readings",
                Timestamp.class
        )).thenReturn(null);
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbcTemplate)
                .queryForObject(anyString(), org.mockito.ArgumentMatchers.<RowMapper<SensorConnectionLogResponse>>any());

        SensorConnectionService service = new SensorConnectionService(
                jdbcTemplate,
                sensorSseService,
                0
        );
        service.initialize();

        service.checkForTimeout();
        service.checkForTimeout();

        Timestamp readingAt = Timestamp.from(Instant.now());
        SensorReadingResponse reading = new SensorReadingResponse(
                1L,
                1,
                new BigDecimal("55.00"),
                new BigDecimal("45.00"),
                new BigDecimal("35.00"),
                new BigDecimal("55.00"),
                "NORMAL",
                "NORMAL",
                "NORMAL",
                "NORMAL",
                readingAt
        );

        service.recordReadingReceived(reading);
        service.recordReadingReceived(reading);

        verify(jdbcTemplate, times(1)).update(
                startsWith("INSERT INTO sensor_connection_logs"),
                eq("DISCONNECTED"),
                eq("NA"),
                isNull(),
                any(Timestamp.class)
        );
        verify(jdbcTemplate, times(1)).update(
                startsWith("INSERT INTO sensor_connection_logs"),
                eq("RECONNECTED"),
                eq("NA"),
                eq(readingAt),
                any(Timestamp.class)
        );
        verify(sensorSseService, times(2)).publishConnectionStatus(any());
    }
}
