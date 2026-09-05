package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.CompostBatchRequest;
import com.group11.compostsystem.dto.CompostBatchStatusRequest;
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

    @Test
    @SuppressWarnings("unchecked")
    void createBatchUsesTheCurrentWeightBasedProcedureContract() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CompostBatchService service = new CompostBatchService(jdbc);
        CompostBatchRequest request = batchRequest();
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(null);

        service.createBatch(request, 7);

        verify(jdbc).queryForObject(
                eq("CALL sp_create_compost_batch(?, ?, ?, ?, ?, ?, ?, ?)"),
                any(RowMapper.class),
                eq("Batch 4"), eq("Food waste"), eq("Vegetables"), eq("HALF"),
                eq(java.sql.Date.valueOf("2026-09-05")), isNull(), eq("Test batch"), eq(7)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void editBatchUsesTheSameWeightBasedProcedureContract() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CompostBatchService service = new CompostBatchService(jdbc);
        CompostBatchRequest request = batchRequest();
        request.setCurrentPassword("secret123");
        when(jdbc.queryForObject("CALL sp_verify_user_password(?, ?)", Integer.class, 7, "secret123"))
                .thenReturn(1);
        when(jdbc.queryForObject(startsWith("CALL sp_update_compost_batch"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(null);

        service.updateBatch(4, request, 7);

        verify(jdbc).queryForObject(
                eq("CALL sp_update_compost_batch(?, ?, ?, ?, ?, ?, ?, ?)"),
                any(RowMapper.class),
                eq(4), eq("Batch 4"), eq("Food waste"), eq("Vegetables"), eq("HALF"),
                eq(java.sql.Date.valueOf("2026-09-05")), isNull(), eq("Test batch")
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void activateAndStatusActionsUseTheirExpectedProcedures() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CompostBatchService service = new CompostBatchService(jdbc);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(null);
        CompostBatchStatusRequest statusRequest = new CompostBatchStatusRequest();
        statusRequest.setStatus("ready");

        service.setActiveBatch(4);
        service.updateBatchStatus(4, statusRequest);

        verify(jdbc).queryForObject(
                eq("CALL sp_set_active_compost_batch(?)"), any(RowMapper.class), eq(4)
        );
        verify(jdbc).queryForObject(
                eq("CALL sp_update_compost_batch_status(?, ?, ?)"),
                any(RowMapper.class), eq(4), eq("READY"), isNull()
        );
    }

    @Test
    void invalidStatusIsRejectedBeforeCallingTheDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CompostBatchService service = new CompostBatchService(jdbc);
        CompostBatchStatusRequest request = new CompostBatchStatusRequest();
        request.setStatus("BROKEN");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateBatchStatus(4, request)
        );

        assertEquals("Invalid compost batch status.", error.getMessage());
        verifyNoInteractions(jdbc);
    }

    private CompostBatchRequest batchRequest() {
        CompostBatchRequest request = new CompostBatchRequest();
        request.setBatchName(" Batch 4 ");
        request.setPrimaryMaterial(" Food waste ");
        request.setMaterialDescription(" Vegetables ");
        request.setFillLevel("HALF");
        request.setStartDate("2026-09-05");
        request.setNotes(" Test batch ");
        return request;
    }
}
