package com.group11.compostsystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group11.compostsystem.dto.AIPredictionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PredictionServiceTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final GeminiPredictionClient gemini = mock(GeminiPredictionClient.class);
    private final PredictionService service = new PredictionService(jdbc, new ObjectMapper(), gemini);

    @BeforeEach
    void batchExists() {
        when(jdbc.queryForList("CALL sp_get_compost_batch_by_id(?)", 3))
                .thenReturn(List.of(Map.of("batch_id", 3, "status", "ACTIVE")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dailyQueryUsesManilaMidnightAsAnInstantEvenOnUtcServers() {
        when(jdbc.queryForObject(contains("created_at >= ?"), any(RowMapper.class), eq(3), any(), any()))
                .thenThrow(new EmptyResultDataAccessException(1));
        service.getPredictionAvailability(3);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Manila"));
        verify(jdbc).queryForObject(contains("created_at >= ?"), any(RowMapper.class), eq(3),
                eq(Timestamp.from(today.atStartOfDay(ZoneId.of("Asia/Manila")).toInstant())),
                eq(Timestamp.from(today.plusDays(1).atStartOfDay(ZoneId.of("Asia/Manila")).toInstant())));
        verifyNoInteractions(gemini);
    }

    @Test
    @SuppressWarnings("unchecked")
    void savedDailyPredictionDoesNotCallGeminiAgain() {
        AIPredictionResponse saved = new AIPredictionResponse();
        saved.setSuccess(true);
        saved.setPredictionId(12);
        when(jdbc.queryForObject(contains("created_at >= ?"), any(RowMapper.class), eq(3), any(), any()))
                .thenReturn(saved);
        assertSame(saved, service.generatePrediction(3));
        verifyNoInteractions(gemini);
    }

    @Test
    @SuppressWarnings("unchecked")
    void providerFailureDoesNotSaveOrConsumeTheDailyLimit() {
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));
        when(jdbc.queryForList("CALL sp_get_latest_sensor_reading_for_batch(?)", 3))
                .thenReturn(List.of(Map.of("reading_id", 10, "batch_id", 3)));
        when(jdbc.queryForMap(anyString(), eq(3))).thenReturn(Map.of("total_readings", 1));
        when(gemini.generate(anyString())).thenThrow(new GeminiPredictionClient.PredictionUnavailableException("AI usage limit reached."));
        AIPredictionResponse result = service.generatePrediction(3);
        assertFalse(result.isSuccess());
        assertEquals("AI usage limit reached.", result.getMessage());
        assertNull(result.getNextPredictionAt());
        verify(jdbc, never()).queryForObject(startsWith("CALL sp_save_ai_prediction"), any(RowMapper.class), any(Object[].class));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        var prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(gemini).generate(prompt.capture());
        assertTrue(prompt.getValue().contains("\"batch_id\":3"));
        assertTrue(prompt.getValue().contains("\"batch_weight_kg\":5"));
    }

    @Test
    void completedBatchCannotGeneratePrediction() {
        when(jdbc.queryForList("CALL sp_get_compost_batch_by_id(?)", 3))
                .thenReturn(List.of(Map.of("batch_id", 3, "status", "COMPLETED")));

        AIPredictionResponse result = service.generatePrediction(3);

        assertFalse(result.isSuccess());
        assertEquals(
                "AI prediction is only available for ongoing compost batches. The selected batch is completed.",
                result.getMessage()
        );
        verifyNoInteractions(gemini);
        verify(jdbc, never()).queryForList("CALL sp_get_latest_sensor_reading_for_batch(?)", 3);
    }

    @Test
    void terminatedBatchAvailabilityExplainsRestriction() {
        when(jdbc.queryForList("CALL sp_get_compost_batch_by_id(?)", 3))
                .thenReturn(List.of(Map.of("batch_id", 3, "status", "CANCELLED")));

        var availability = service.getPredictionAvailability(3);

        assertFalse(availability.isCanGenerate());
        assertEquals(
                "AI prediction is only available for ongoing compost batches. The selected batch is terminated.",
                availability.getMessage()
        );
        assertNull(availability.getNextPredictionAt());
        assertNull(availability.getPrediction());
        verifyNoInteractions(gemini);
    }
}
