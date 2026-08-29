package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.ThresholdSettingsRequest;
import com.group11.compostsystem.dto.ThresholdSettingsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ThresholdServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void savesValidatedSettingsWithConfiguredActuatorTiming() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ThresholdService service = new ThresholdService(jdbc);
        ThresholdSettingsResponse current = settings("50", "60", 60, 15, 5, 30, 30);
        ThresholdSettingsResponse saved = settings("45", "55", 60, 15, 5, 30, 30);

        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM users"), eq(Integer.class), eq(7), eq("secret123")))
                .thenReturn(1);
        when(jdbc.queryForObject(contains("FROM threshold_settings"), any(RowMapper.class)))
                .thenReturn(current, saved);

        ThresholdSettingsRequest request = request("45", "55", 30, 30, "secret123");
        ThresholdSettingsResponse result = service.saveThresholdSettings(request, 7);

        assertSame(saved, result);
        var values = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("INSERT INTO threshold_settings"), values.capture());
        assertEquals(
                Arrays.asList(new BigDecimal("45"), new BigDecimal("55"), 60, 15, 5, 30, 30, 7),
                Arrays.asList(values.getValue())
        );
        verify(jdbc, never()).queryForObject(startsWith("CALL sp_save_threshold_settings"), any(RowMapper.class));
    }

    @Test
    void rejectsOutOfRangeThresholdsBeforeWriting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ThresholdService service = new ThresholdService(jdbc);
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM users"), eq(Integer.class), anyInt(), anyString()))
                .thenReturn(1);
        when(jdbc.queryForObject(contains("FROM threshold_settings"), any(RowMapper.class)))
                .thenReturn(settings("50", "60", 60, 15, 5, 30, 30));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveThresholdSettings(request("101", "55", 30, 30, "secret123"), 7)
        );

        assertEquals("Moisture minimum must be a percentage between 0 and 100.", error.getMessage());
        verify(jdbc, never()).update(contains("INSERT INTO threshold_settings"), any(Object[].class));
    }

    @Test
    void rejectsNegativeCooldownsAndIncorrectPassword() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ThresholdService service = new ThresholdService(jdbc);
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM users"), eq(Integer.class), anyInt(), anyString()))
                .thenReturn(1, 0);
        when(jdbc.queryForObject(contains("FROM threshold_settings"), any(RowMapper.class)))
                .thenReturn(settings("50", "60", 60, 15, 5, 30, 30));

        assertThrows(IllegalArgumentException.class,
                () -> service.saveThresholdSettings(request("45", "55", -1, 30, "secret123"), 7));
        assertEquals(
                "Current password is incorrect.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.saveThresholdSettings(request("45", "55", 30, 30, "wrong"), 7)).getMessage()
        );
        verify(jdbc, never()).update(contains("INSERT INTO threshold_settings"), any(Object[].class));
    }

    @Test
    void saveIsTransactional() throws Exception {
        assertNotNull(ThresholdService.class
                .getMethod("saveThresholdSettings", ThresholdSettingsRequest.class, Integer.class)
                .getAnnotation(Transactional.class));
    }

    private ThresholdSettingsRequest request(String moisture, String gas, int sprayCooldown,
                                             int fanCooldown, String password) {
        ThresholdSettingsRequest request = new ThresholdSettingsRequest();
        request.setMoistureMin(new BigDecimal(moisture));
        request.setGasMax(new BigDecimal(gas));
        request.setSprayCooldownSeconds(sprayCooldown);
        request.setFanCooldownSeconds(fanCooldown);
        request.setCurrentPassword(password);
        return request;
    }

    private ThresholdSettingsResponse settings(String moisture, String gas, int interval,
                                               int sprayDuration, int fanDuration,
                                               int sprayCooldown, int fanCooldown) {
        return new ThresholdSettingsResponse(
                new BigDecimal(moisture), new BigDecimal(gas), interval,
                sprayDuration, fanDuration, sprayCooldown, fanCooldown, 7, null
        );
    }
}
