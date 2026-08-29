package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.RegisterRequest;
import com.group11.compostsystem.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceRegistrationTests {
    @Test
    @SuppressWarnings("unchecked")
    void registrationWritesACompatibleSaltedHashWithoutCallingTheOldProcedure() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuthService service = new AuthService(jdbc);
        UserResponse user = new UserResponse(7L, "Test User", "test@example.com", "OPERATOR");
        when(jdbc.queryForObject(contains("FROM users"), any(RowMapper.class), eq("test@example.com")))
                .thenReturn(user);
        RegisterRequest request = new RegisterRequest();
        request.setName(" Test User "); request.setEmail("TEST@example.com");
        request.setPassword("password1"); request.setConfirmPassword("password1");

        var result = service.register(request, "127.0.0.1", "browser");
        assertSame(user, result.getUser());
        assertNotNull(result.getSessionToken());
        assertNotNull(result.getExpiresAt());
        var values = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("INSERT INTO users"), values.capture());
        Object[] inserted = values.getValue();
        assertEquals(Arrays.asList("Test User", "test@example.com"), Arrays.asList(inserted[0], inserted[1]));
        assertEquals(64, inserted[2].toString().length());
        assertEquals(64, inserted[3].toString().length());
        assertNotEquals(inserted[2], inserted[3]);
        verify(jdbc).update(contains("INSERT INTO user_sessions"),
                eq(7L), anyString(), any(), eq("127.0.0.1"), eq("browser"));
        verify(jdbc, never()).queryForObject(startsWith("CALL sp_register_user"), any(RowMapper.class), any(Object[].class));
        verify(jdbc, never()).queryForObject(startsWith("CALL sp_create_user_session"), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void registerIsTransactionalSoSessionFailureRollsBackTheUser() throws Exception {
        assertNotNull(AuthService.class
                .getMethod("register", RegisterRequest.class, String.class, String.class)
                .getAnnotation(Transactional.class));
    }
}
