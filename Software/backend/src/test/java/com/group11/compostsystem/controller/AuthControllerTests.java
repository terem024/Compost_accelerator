package com.group11.compostsystem.controller;

import com.group11.compostsystem.service.AuthService;
import com.group11.compostsystem.service.OtpService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AuthControllerTests {
    private final AuthService auth = mock(AuthService.class);
    private final OtpService otp = mock(OtpService.class);
    private final AuthController controller = new AuthController(auth, otp);

    @Test
    void databaseFailureIsNotReportedAsAnExpiredSession() {
        when(auth.validateSession("test-token")).thenThrow(new DataAccessResourceFailureException("unavailable"));
        assertEquals(503, controller.getSession("Bearer test-token").getStatusCode().value());
    }

    @Test
    void expiredOrRevokedSessionIsUnauthorized() {
        when(auth.validateSession("test-token")).thenThrow(new EmptyResultDataAccessException(1));
        assertEquals(401, controller.getSession("Bearer test-token").getStatusCode().value());
    }

    @Test
    void missingTokenIsUnauthorizedWithoutCallingDatabase() {
        assertEquals(401, controller.getSession(null).getStatusCode().value());
        verifyNoInteractions(auth);
    }

    @Test
    void databaseFailureDoesNotConsumeTheVerifiedEmail() {
        var request = new com.group11.compostsystem.dto.RegisterRequest();
        request.setName("Test User"); request.setEmail("test@example.com");
        request.setPassword("password1"); request.setConfirmPassword("password1");
        doThrow(new DataAccessResourceFailureException("unavailable"))
                .when(auth).register(eq(request), anyString(), any());
        var servlet = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(servlet.getRemoteAddr()).thenReturn("127.0.0.1");
        assertEquals(503, controller.register(request, servlet).getStatusCode().value());
        verify(otp).requireVerifiedEmail("test@example.com");
        verify(otp, never()).consumeVerifiedEmail(anyString());
    }

    @Test
    void duplicateEmailGetsAnActionableMessage() {
        var request = new com.group11.compostsystem.dto.RegisterRequest();
        request.setName("Test User"); request.setEmail("test@example.com");
        request.setPassword("password1"); request.setConfirmPassword("password1");
        doThrow(new DuplicateKeyException("duplicate"))
                .when(auth).register(eq(request), anyString(), any());
        var servlet = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(servlet.getRemoteAddr()).thenReturn("127.0.0.1");
        var response = controller.register(request, servlet);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("Email is already registered.", response.getBody().getMessage());
    }
}
