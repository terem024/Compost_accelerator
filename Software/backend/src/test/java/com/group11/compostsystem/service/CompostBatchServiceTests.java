package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.CompostBatchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class CompostBatchServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void batchListUsesTheNewestPredictionDateInsteadOfTheCachedBatchDate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CompostBatchService service = new CompostBatchService(jdbc);
        when(jdbc.query(any(String.class), any(RowMapper.class))).thenReturn(List.of());

        service.getBatches();

        verify(jdbc).query(
                contains("ORDER BY ap.created_at DESC, ap.prediction_id DESC"),
                any(RowMapper.class)
        );
    }

    @Test
    void legacyOneThirdOptionIsRejectedForNewBatches() {
        CompostBatchService service = new CompostBatchService(mock(JdbcTemplate.class));
        CompostBatchRequest request = new CompostBatchRequest();
        request.setBatchName("Batch");
        request.setPrimaryMaterial("Food waste");
        request.setStartDate("2026-09-01");
        request.setFillLevel("ONE_THIRD");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.createBatch(request, 1)
        );

        assertEquals("Compost batch weight must be 5 kg or 10 kg.", error.getMessage());
    }
}
