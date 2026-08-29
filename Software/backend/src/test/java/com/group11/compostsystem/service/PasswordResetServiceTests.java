package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.ForgotPasswordRequest;
import com.group11.compostsystem.dto.ResetPasswordRequest;
import com.group11.compostsystem.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordResetServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void createsHostedResetLinkAndSendsItThroughConfiguredEmailService() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        EmailService email = mock(EmailService.class);
        PasswordResetService service = service(jdbc, email);
        when(jdbc.queryForObject(contains("FROM users"), any(RowMapper.class), eq("user@example.com")))
                .thenReturn(user());

        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(" USER@example.com ");
        service.requestPasswordReset(request, "127.0.0.1", "browser");

        verify(jdbc).update(contains("INSERT INTO password_reset_tokens"),
                eq(7L), anyString(), any(), eq("127.0.0.1"), eq("browser"));
        verify(email).sendPasswordResetEmail(eq("user@example.com"),
                startsWith("https://frontend-production-0141.up.railway.app/reset-password?token="));
        verify(jdbc, never()).queryForObject(startsWith("CALL sp_get_user_by_email"), any(RowMapper.class), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void expiresGeneratedTokenWhenEmailDeliveryFails() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        EmailService email = mock(EmailService.class);
        PasswordResetService service = service(jdbc, email);
        when(jdbc.queryForObject(contains("FROM users"), any(RowMapper.class), anyString())).thenReturn(user());
        doThrow(new IllegalStateException("provider unavailable"))
                .when(email).sendPasswordResetEmail(anyString(), anyString());

        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("user@example.com");
        assertThrows(IllegalStateException.class,
                () -> service.requestPasswordReset(request, null, null));

        verify(jdbc).update(contains("WHERE token_hash = ? AND status = 'ACTIVE'"), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updatesPasswordConsumesTokenAndRevokesSessions() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PasswordResetService service = service(jdbc, mock(EmailService.class));
        when(jdbc.queryForObject(contains("FROM password_reset_tokens prt"), any(RowMapper.class), anyString()))
                .thenReturn(user());
        when(jdbc.update(startsWith("UPDATE users SET password_hash"), any(Object[].class))).thenReturn(1);
        when(jdbc.update(contains("SET status = 'USED'"), any(Object[].class))).thenReturn(1);

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("valid-token");
        request.setNewPassword("newpassword1");
        request.setConfirmPassword("newpassword1");
        service.resetPassword(request);

        verify(jdbc).update(startsWith("UPDATE users SET password_hash"), anyString(), anyString(), eq(7L));
        verify(jdbc).update(contains("SET status = 'USED'"), anyString());
        verify(jdbc).update(contains("UPDATE user_sessions SET status = 'REVOKED'"), eq(7L));
    }

    @Test
    void resetIsTransactional() throws Exception {
        assertNotNull(PasswordResetService.class
                .getMethod("resetPassword", ResetPasswordRequest.class)
                .getAnnotation(Transactional.class));
    }

    private PasswordResetService service(JdbcTemplate jdbc, EmailService email) {
        PasswordResetService service = new PasswordResetService(jdbc, email);
        ReflectionTestUtils.setField(service, "frontendUrl", "https://frontend-production-0141.up.railway.app/");
        ReflectionTestUtils.setField(service, "resetTokenExpirationMinutes", 30L);
        return service;
    }

    private UserResponse user() {
        return new UserResponse(7L, "Test User", "user@example.com", "OPERATOR");
    }
}
