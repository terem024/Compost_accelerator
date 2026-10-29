package com.group11.compostsystem.controller;

import com.group11.compostsystem.dto.AuthResult;
import com.group11.compostsystem.dto.ForgotPasswordRequest;
import com.group11.compostsystem.dto.UserResponse;
import com.group11.compostsystem.service.AuthService;
import com.group11.compostsystem.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordResetControllerTests {
    private final PasswordResetService reset = mock(PasswordResetService.class);
    private final AuthService auth = mock(AuthService.class);
    private final HttpServletRequest http = mock(HttpServletRequest.class);
    private final PasswordResetController controller = new PasswordResetController(reset, auth);

    private void signIn() {
        when(auth.getCurrentSession("token")).thenReturn(new AuthResult(
                new UserResponse(7L, "Operator", "owner@example.com", "OPERATOR"), "token", new Timestamp(Long.MAX_VALUE)));
    }

    @Test
    void authenticatedSuccessConfirmsSendOnlyForSessionOwner() {
        signIn();
        when(reset.requestPasswordReset(any(), any(), any())).thenReturn(true);
        var response = controller.sendOwnResetEmail("Bearer token", http);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isSuccess());
        assertEquals(PasswordResetService.RESET_EMAIL_SENT_MESSAGE, response.getBody().getMessage());
        verify(reset).requestPasswordReset(argThat(request -> request.getEmail().equals("owner@example.com")), any(), any());
    }

    @Test
    void providerFailureIsAnErrorNotSuccess() {
        signIn();
        when(reset.requestPasswordReset(any(), any(), any())).thenThrow(new IllegalStateException("private provider error"));
        var response = controller.sendOwnResetEmail("Bearer token", http);
        assertEquals(503, response.getStatusCode().value());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("couldn't send"));
        assertFalse(response.getBody().getMessage().contains("private"));
    }

    @Test
    void missingOrExpiredSessionCannotRequestConfirmedSend() {
        assertEquals(401, controller.sendOwnResetEmail(null, http).getStatusCode().value());
        when(auth.getCurrentSession("expired")).thenThrow(new EmptyResultDataAccessException(1));
        assertEquals(401, controller.sendOwnResetEmail("Bearer expired", http).getStatusCode().value());
        verifyNoInteractions(reset);
    }

    @Test
    void publicResponseDoesNotRevealWhetherAccountExists() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("user@example.com");
        when(reset.requestPasswordReset(any(), any(), any())).thenReturn(true, false);
        var existing = controller.forgotPassword(request, http);
        var missing = controller.forgotPassword(request, http);
        assertEquals(existing.getBody().getMessage(), missing.getBody().getMessage());
        assertEquals(PasswordResetService.FORGOT_PASSWORD_MESSAGE, existing.getBody().getMessage());
    }

    @Test
    void databaseFailureDoesNotShowFalseSuccess() {
        when(reset.requestPasswordReset(any(), any(), any())).thenThrow(new DataAccessResourceFailureException("private database error"));
        var response = controller.forgotPassword(new ForgotPasswordRequest(), http);
        assertEquals(503, response.getStatusCode().value());
        assertFalse(response.getBody().isSuccess());
    }
}
