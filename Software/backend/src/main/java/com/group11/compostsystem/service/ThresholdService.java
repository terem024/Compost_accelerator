package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.ThresholdSettingsRequest;
import com.group11.compostsystem.dto.ThresholdSettingsResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ThresholdService {

    private final JdbcTemplate jdbcTemplate;

    public ThresholdService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ThresholdSettingsResponse getThresholdSettings() {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT moisture_min, gas_max, reading_interval_seconds,
                           spray_duration_seconds, fan_duration_seconds,
                           spray_cooldown_seconds, fan_cooldown_seconds,
                           updated_by, updated_at
                    FROM threshold_settings
                    ORDER BY setting_id DESC
                    LIMIT 1
                    """,
                    (rs, rowNum) -> new ThresholdSettingsResponse(
                            rs.getBigDecimal("moisture_min"),
                            rs.getBigDecimal("gas_max"),
                            rs.getInt("reading_interval_seconds"),
                            rs.getInt("spray_duration_seconds"),
                            rs.getInt("fan_duration_seconds"),
                            rs.getInt("spray_cooldown_seconds"),
                            rs.getInt("fan_cooldown_seconds"),
                            rs.getObject("updated_by", Integer.class),
                            rs.getTimestamp("updated_at")
                    )
            );
        } catch (EmptyResultDataAccessException ex) {
            return defaultThresholdSettings();
        }
    }

    @Transactional
    public ThresholdSettingsResponse saveThresholdSettings(ThresholdSettingsRequest request, Integer updatedByUserId) {
        if (updatedByUserId == null) {
            throw new IllegalArgumentException("A valid logged-in user is required to update threshold settings.");
        }

        if (request == null) {
            throw new IllegalArgumentException("Threshold settings are required.");
        }

        if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
            throw new IllegalArgumentException("Current password is required before saving threshold settings.");
        }

        if (!isCurrentPasswordValid(updatedByUserId, request.getCurrentPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }

        ThresholdSettingsResponse current = getThresholdSettings();
        BigDecimal moistureMin = valueOrDefault(request.getMoistureMin(), current.getMoistureMin());
        BigDecimal gasMax = valueOrDefault(request.getGasMax(), current.getGasMax());
        validatePercentage(moistureMin, "Moisture minimum");
        validatePercentage(gasMax, "Gas maximum");
        Integer readingInterval = valueOrDefault(request.getReadingIntervalSeconds(), current.getReadingIntervalSeconds());
        Integer sprayDuration = valueOrDefault(request.getSprayDurationSeconds(), current.getSprayDurationSeconds());
        Integer fanDuration = valueOrDefault(request.getFanDurationSeconds(), current.getFanDurationSeconds());
        Integer sprayCooldown = valueOrDefault(request.getSprayCooldownSeconds(), current.getSprayCooldownSeconds());
        Integer fanCooldown = valueOrDefault(request.getFanCooldownSeconds(), current.getFanCooldownSeconds());
        validatePositive(readingInterval, "Reading interval");
        validatePositive(sprayDuration, "Spray duration");
        validatePositive(fanDuration, "Fan duration");
        validateNonNegative(sprayCooldown, "Spray cooldown");
        validateNonNegative(fanCooldown, "Fan cooldown");

        jdbcTemplate.update(
                """
                INSERT INTO threshold_settings
                    (moisture_min, gas_max, reading_interval_seconds,
                     spray_duration_seconds, fan_duration_seconds,
                     spray_cooldown_seconds, fan_cooldown_seconds, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                moistureMin,
                gasMax,
                readingInterval,
                sprayDuration,
                fanDuration,
                sprayCooldown,
                fanCooldown,
                updatedByUserId
        );
        return getThresholdSettings();
    }

    private boolean isCurrentPasswordValid(Integer userId, String currentPassword) {
        Integer passwordValid = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM users
                WHERE user_id = ? AND password_salt IS NOT NULL
                  AND password_hash = SHA2(CONCAT(password_salt, ?), 256)
                """,
                Integer.class,
                userId,
                currentPassword
        );

        return passwordValid != null && passwordValid == 1;
    }

    private void validatePercentage(BigDecimal value, String label) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0
                || value.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException(label + " must be a percentage between 0 and 100.");
        }
    }

    private void validatePositive(Integer value, String label) {
        if (value == null || value < 1) throw new IllegalArgumentException(label + " must be at least 1 second.");
    }

    private void validateNonNegative(Integer value, String label) {
        if (value == null || value < 0) throw new IllegalArgumentException(label + " cannot be negative.");
    }

    private ThresholdSettingsResponse defaultThresholdSettings() {
        return new ThresholdSettingsResponse(
                new BigDecimal("50"),
                new BigDecimal("60"),
                60,
                15,
                5,
                30,
                30,
                null,
                null
        );
    }

    private BigDecimal valueOrDefault(BigDecimal value, BigDecimal fallback) {
        return value != null ? value : fallback;
    }

    private Integer valueOrDefault(Integer value, Integer fallback) {
        return value != null ? value : fallback;
    }
}
