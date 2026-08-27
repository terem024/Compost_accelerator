package com.group11.compostsystem.controller;

import com.group11.compostsystem.service.AuthService;
import com.group11.compostsystem.service.OtpService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AuthControllerTests {
    private final AuthService auth = mock(AuthService.class);
    private final AuthController controller = new AuthController(auth, mock(OtpService.class));

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
}
