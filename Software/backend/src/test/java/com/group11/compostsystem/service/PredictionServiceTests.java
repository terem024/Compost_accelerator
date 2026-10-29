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
    private final FallbackPredictionClient predictionClient = mock(FallbackPredictionClient.class);
    private final PredictionService service = new PredictionService(jdbc, new ObjectMapper(), predictionClient);

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
        verifyNoInteractions(predictionClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void savedDailyPredictionDoesNotCallEitherProviderAgain() {
        AIPredictionResponse saved = new AIPredictionResponse();
        saved.setSuccess(true);
        saved.setPredictionId(12);
        when(jdbc.queryForObject(contains("created_at >= ?"), any(RowMapper.class), eq(3), any(), any()))
                .thenReturn(saved);
        assertSame(saved, service.generatePrediction(3));
        verifyNoInteractions(predictionClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void providerFailureDoesNotSaveOrConsumeTheDailyLimit() {
        when(jdbc.queryForList("CALL sp_get_compost_batch_by_id(?)", 3))
                .thenReturn(List.of(Map.of("batch_id", 3, "status", "ACTIVE",
                        "actual_ready_date", "2026-09-12", "latest_predicted_ready_date", "2026-09-12")));
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));
        when(jdbc.queryForList("CALL sp_get_latest_sensor_reading_for_batch(?)", 3))
                .thenReturn(List.of(Map.of("reading_id", 10, "batch_id", 3)));
        when(jdbc.queryForMap(anyString(), eq(3))).thenReturn(Map.of("total_readings", 1));
        when(predictionClient.generate(anyString())).thenThrow(new GeminiPredictionClient.PredictionUnavailableException("AI usage limit reached."));
        AIPredictionResponse result = service.generatePrediction(3);
        assertFalse(result.isSuccess());
        assertEquals("AI usage limit reached.", result.getMessage());
        assertNull(result.getNextPredictionAt());
        verify(jdbc, never()).queryForObject(startsWith("CALL sp_save_ai_prediction"), any(RowMapper.class), any(Object[].class));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        var prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(predictionClient).generate(prompt.capture());
        assertTrue(prompt.getValue().contains("\"batch_id\":3"));
        assertTrue(prompt.getValue().contains("\"batch_weight_kg\":5"));
        assertFalse(prompt.getValue().contains("\"actual_ready_date\""));
        assertFalse(prompt.getValue().contains("\"latest_predicted_ready_date\""));
    }

    @Test
    @SuppressWarnings("unchecked")
    void backupResultIsSavedWithActualProviderAndReusedWithoutAnotherProviderCall() throws Exception {
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));
        AIPredictionResponse saved = new AIPredictionResponse();
        saved.setSuccess(true);
        saved.setPredictionId(55);
        when(jdbc.queryForObject(contains("created_at >= ?"), any(RowMapper.class), eq(3), any(), any()))
                .thenThrow(new EmptyResultDataAccessException(1)).thenReturn(saved);
        when(jdbc.queryForList("CALL sp_get_latest_sensor_reading_for_batch(?)", 3))
                .thenReturn(List.of(Map.of("reading_id", 10, "batch_id", 3)));
        when(jdbc.queryForMap(anyString(), eq(3))).thenReturn(Map.of("total_readings", 1));
        when(jdbc.queryForObject(startsWith("CALL sp_save_ai_prediction"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(55);
        String raw = FallbackPredictionClientTests.VALID;
        when(predictionClient.generate(anyString())).thenReturn(new FallbackPredictionClient.Result(
                "Groq", "openai/gpt-oss-120b", raw, new ObjectMapper().readTree(raw)));

        assertSame(saved, service.generatePrediction(3));
        assertSame(saved, service.generatePrediction(3));
        verify(predictionClient, times(1)).generate(anyString());
        var args = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(startsWith("CALL sp_save_ai_prediction"), any(RowMapper.class), args.capture());
        assertEquals("openai/gpt-oss-120b", args.getValue()[11]);
        assertEquals(raw, args.getValue()[13]);
        verify(jdbc).update("UPDATE ai_predictions SET model_provider = ? WHERE prediction_id = ?", "Groq", 55);
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
        verifyNoInteractions(predictionClient);
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
        verifyNoInteractions(predictionClient);
    }

    @Test
    void pastReadinessDateIsNotReportedAsConfirmedMaturity() {
        var result = estimate(LocalDate.of(2026, 9, 12), 0);
        result.setPredictionSummary("The compost is ready for use.");
        PredictionService.reviewEstimate(result, LocalDate.of(2026, 10, 26));
        assertNull(result.getEstimatedReadyDate());
        assertNull(result.getEstimatedDaysRemaining());
        assertNotNull(result.getEstimateWarning());
        assertFalse(result.getPredictionSummary().contains("ready for use"));
    }

    @Test
    void inconsistentFutureDateIsRejectedRatherThanInventingAnotherDate() {
        var result = estimate(LocalDate.of(2026, 11, 1), 40);
        PredictionService.reviewEstimate(result, LocalDate.of(2026, 10, 26));
        assertNull(result.getEstimatedReadyDate());
    }

    @Test
    void matchingFutureEstimateIsPreserved() {
        var result = estimate(LocalDate.of(2026, 11, 1), 6);
        PredictionService.reviewEstimate(result, LocalDate.of(2026, 10, 26));
        assertEquals(LocalDate.of(2026, 11, 1), result.getEstimatedReadyDate());
        assertEquals(6, result.getEstimatedDaysRemaining());
        assertNull(result.getEstimateWarning());
    }

    @Test
    void savedEstimateUsesItsGenerationDateAndUpdatesRemainingDays() {
        var result = estimate(LocalDate.of(2026, 11, 1), 7);
        result.setPredictionCreatedAt(java.time.OffsetDateTime.parse("2026-10-25T09:00:00+08:00"));
        PredictionService.reviewEstimate(result, LocalDate.of(2026, 10, 26));
        assertEquals(6, result.getEstimatedDaysRemaining());
        assertNotNull(result.getEstimatedReadyDate());
    }

    @Test
    void missingDateOrZeroDaysRequiresInspection() {
        for (var result : List.of(estimate(null, 4), estimate(LocalDate.of(2026, 11, 1), 0), estimate(null, null))) {
            PredictionService.reviewEstimate(result, LocalDate.of(2026, 10, 26));
            assertNull(result.getEstimatedReadyDate());
            assertNull(result.getEstimatedDaysRemaining());
            assertNotNull(result.getEstimateWarning());
        }
    }

    private AIPredictionResponse estimate(LocalDate date, Integer days) {
        var result = new AIPredictionResponse();
        result.setEstimatedReadyDate(date);
        result.setEstimatedDaysRemaining(days);
        return result;
    }
}
