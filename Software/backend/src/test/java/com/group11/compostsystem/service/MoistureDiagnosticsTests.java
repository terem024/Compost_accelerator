package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.MoistureProbeDiagnostic;
import com.group11.compostsystem.dto.SensorReadingRequest;
import com.group11.compostsystem.dto.SensorReadingResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MoistureDiagnosticsTests {
    private final SensorReadingService service = new SensorReadingService(null, null, null, null, null);

    @Test
    void acceptsLegacyPayloadsAndKeepsRailValuesForDiagnosis() {
        assertDoesNotThrow(() -> service.validateMoistureDiagnostics(null));
        assertDoesNotThrow(() -> service.validateMoistureDiagnostics(List.of(
                new MoistureProbeDiagnostic(1, 36, 4095, null, true, false),
                new MoistureProbeDiagnostic(2, 35, 0, null, true, false))));
    }

    @Test
    void preservesZeroPercentAndDoesNotAverageInvalidProbeDiagnostics() {
        SensorReadingRequest request = new SensorReadingRequest();
        request.setMoisturePercent2(BigDecimal.ZERO);
        request.setMoistureProbes(List.of(
                new MoistureProbeDiagnostic(1, 36, 4095, null, true, false),
                new MoistureProbeDiagnostic(2, 35, 3300, BigDecimal.ZERO, true, true)));
        assertDoesNotThrow(() -> service.validateMoistureDiagnostics(request.getMoistureProbes()));
        assertEquals(BigDecimal.ZERO, request.getEffectiveMoistureLevel());
    }

    @Test
    void rejectsDuplicateProbesAndOutOfRangeRawValuesBeforeSaving() {
        var probe = new MoistureProbeDiagnostic(1, 36, 3000, BigDecimal.TEN, true, true);
        assertThrows(IllegalArgumentException.class, () -> service.validateMoistureDiagnostics(List.of(probe, probe)));
        assertThrows(IllegalArgumentException.class, () -> service.validateMoistureDiagnostics(List.of(
                new MoistureProbeDiagnostic(1, 36, 5000, null, true, false))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void latestReadingRestoresSavedDiagnosticsAfterRestart() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var reading = new SensorReadingResponse(42L, 5, BigDecimal.TEN, null, null, null,
                "LOW", null, null, null, null);
        var probes = List.of(new MoistureProbeDiagnostic(1, 36, 4095, null, true, false));
        when(jdbc.queryForObject(anyString(), any(RowMapper.class))).thenReturn(reading);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(42L))).thenReturn(probes);
        var persistedService = new SensorReadingService(jdbc, null, null, null, null);
        assertEquals(probes, persistedService.getLatestSensorReading().getMoistureProbes());
    }
}
