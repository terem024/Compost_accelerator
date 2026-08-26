package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.CompostBatchRequest;
import com.group11.compostsystem.dto.CompostBatchResponse;
import com.group11.compostsystem.dto.CompostBatchStatusRequest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

@Service
public class CompostBatchService {

    private static final Set<String> VALID_FILL_LEVELS = Set.of("ONE_THIRD", "HALF", "FULL");

    private final JdbcTemplate jdbcTemplate;

    public CompostBatchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CompostBatchResponse createBatch(CompostBatchRequest request, Integer createdBy) {
        validateBatchRequest(request);

        return jdbcTemplate.queryForObject(
                "CALL sp_create_compost_batch(?, ?, ?, ?, ?, ?, ?, ?)",
                (rs, rowNum) -> mapBatch(rs),
                trim(request.getBatchName()),
                trim(request.getPrimaryMaterial()),
                trimToNull(request.getMaterialDescription()),
                normalizeFillLevel(request.getFillLevel()),
                parseRequiredDate(request.getStartDate(), "Start date is required."),
                trimToNull(request.getBinLocation()),
                trimToNull(request.getNotes()),
                createdBy
        );
    }

    public CompostBatchResponse getActiveBatch() {
        try {
            return jdbcTemplate.queryForObject(
                    "CALL sp_get_active_compost_batch()",
                    (rs, rowNum) -> mapBatch(rs)
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<CompostBatchResponse> getBatches() {
        return jdbcTemplate.query(
                "CALL sp_get_compost_batches()",
                (rs, rowNum) -> mapBatch(rs)
        );
    }

    public CompostBatchResponse getBatchById(Integer batchId) {
        return jdbcTemplate.queryForObject(
                "CALL sp_get_compost_batch_by_id(?)",
                (rs, rowNum) -> mapBatch(rs),
                batchId
        );
    }

    public CompostBatchResponse setActiveBatch(Integer batchId) {
        return jdbcTemplate.queryForObject(
                "CALL sp_set_active_compost_batch(?)",
                (rs, rowNum) -> mapBatch(rs),
                batchId
        );
    }

    public CompostBatchResponse updateBatch(Integer batchId, CompostBatchRequest request, Integer updatedByUserId) {
        if (updatedByUserId == null) {
            throw new IllegalArgumentException("A valid logged-in user is required to update compost batch details.");
        }

        if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
            throw new IllegalArgumentException("Current password is required before saving compost batch changes.");
        }

        if (!isCurrentPasswordValid(updatedByUserId, request.getCurrentPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }

        validateBatchRequest(request);

        return jdbcTemplate.queryForObject(
                "CALL sp_update_compost_batch(?, ?, ?, ?, ?, ?, ?, ?)",
                (rs, rowNum) -> mapBatch(rs),
                batchId,
                trim(request.getBatchName()),
                trim(request.getPrimaryMaterial()),
                trimToNull(request.getMaterialDescription()),
                normalizeFillLevel(request.getFillLevel()),
                parseRequiredDate(request.getStartDate(), "Start date is required."),
                trimToNull(request.getBinLocation()),
                trimToNull(request.getNotes())
        );
    }

    public CompostBatchResponse updateBatchStatus(Integer batchId, CompostBatchStatusRequest request) {
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new IllegalArgumentException("Status is required.");
        }

        return jdbcTemplate.queryForObject(
                "CALL sp_update_compost_batch_status(?, ?, ?)",
                (rs, rowNum) -> mapBatch(rs),
                batchId,
                request.getStatus().trim().toUpperCase(),
                parseOptionalDate(request.getActualReadyDate())
        );
    }

    private void validateBatchRequest(CompostBatchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Batch details are required.");
        }

        if (request.getBatchName() == null || request.getBatchName().isBlank()) {
            throw new IllegalArgumentException("Batch name is required.");
        }

        if (request.getPrimaryMaterial() == null || request.getPrimaryMaterial().isBlank()) {
            throw new IllegalArgumentException("Primary material is required.");
        }

        parseRequiredDate(request.getStartDate(), "Start date is required.");

        normalizeFillLevel(request.getFillLevel());
    }

    private boolean isCurrentPasswordValid(Integer userId, String currentPassword) {
        Integer passwordValid = jdbcTemplate.queryForObject(
                "CALL sp_verify_user_password(?, ?)",
                Integer.class,
                userId,
                currentPassword
        );

        return passwordValid != null && passwordValid == 1;
    }

    private CompostBatchResponse mapBatch(ResultSet rs) throws SQLException {
        return new CompostBatchResponse(
                rs.getInt("batch_id"),
                rs.getString("batch_code"),
                rs.getString("batch_name"),
                rs.getString("primary_material"),
                rs.getString("material_description"),
                rs.getString("fill_level"),
                toDateString(rs.getDate("start_date")),
                toDateString(rs.getDate("latest_predicted_ready_date")),
                toDateString(rs.getDate("actual_ready_date")),
                rs.getString("status"),
                rs.getString("bin_location"),
                rs.getString("notes"),
                rs.getObject("created_by", Integer.class),
                rs.getTimestamp("created_at"),
                rs.getTimestamp("updated_at")
        );
    }

    private Date parseRequiredDate(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return parseDate(value);
    }

    private Date parseOptionalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return parseDate(value);
    }

    private Date parseDate(String value) {
        try {
            return Date.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Date must use YYYY-MM-DD format.");
        }
    }

    private String toDateString(Date date) {
        return date == null ? null : date.toString();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String normalizeFillLevel(String value) {
        String fillLevel = value == null || value.isBlank()
                ? "HALF"
                : value.trim().toUpperCase();

        if (!VALID_FILL_LEVELS.contains(fillLevel)) {
            throw new IllegalArgumentException("Fill level must be ONE_THIRD, HALF, or FULL.");
        }

        return fillLevel;
    }
}
