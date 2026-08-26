package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.AuthResult;
import com.group11.compostsystem.dto.LoginRequest;
import com.group11.compostsystem.dto.RegisterRequest;
import com.group11.compostsystem.dto.UserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class AuthService {

    private static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.session.duration-minutes:60}")
    private long sessionDurationMinutes;

    public AuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void validateRegisterRequest(RegisterRequest request) {
        String name = request == null || request.getName() == null ? "" : request.getName().trim();
        String email = request == null || request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        String password = request == null ? null : request.getPassword();
        String confirmPassword = request == null ? null : request.getConfirmPassword();

        if (name.isBlank()) {
            throw new IllegalArgumentException("Full name is required.");
        }

        if (email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }

        if (!email.matches(EMAIL_PATTERN)) {
            throw new IllegalArgumentException("Email must be a valid address.");
        }

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password field cannot be empty.");
        }

        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }

        if (confirmPassword == null || confirmPassword.isBlank()) {
            throw new IllegalArgumentException("Confirm password field cannot be empty.");
        }

        if (password != null && !password.isBlank() && !password.equals(confirmPassword)) {
            throw new IllegalArgumentException("Password dont match.");
        }
    }

    public AuthResult register(RegisterRequest request, String ipAddress, String userAgent) {
        validateRegisterRequest(request);

        String name = request.getName() == null ? "" : request.getName().trim();
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        String password = request.getPassword();

        UserResponse user = jdbcTemplate.queryForObject(
                "CALL sp_register_user(?, ?, ?)",
                (rs, rowNum) -> new UserResponse(
                        rs.getLong("user_id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("role")
                ),
                name,
                email,
                password
        );

        return createSession(user, ipAddress, userAgent);
    }

    public AuthResult login(LoginRequest request, String ipAddress, String userAgent) {
        String usernameOrEmail = request.getEmail() == null ? "" : request.getEmail().trim();
        String normalizedUsernameOrEmail = usernameOrEmail.toLowerCase();
        String password = request.getPassword();

        if (usernameOrEmail.isBlank()) {
            throw new IllegalArgumentException("Email or username is required.");
        }

        if (password == null || password.isBlank()) {
            recordLoginActivity(null, normalizedUsernameOrEmail, "FAILED", "Password is required.", ipAddress, userAgent);
            throw new IllegalArgumentException("Password is required.");
        }

        try {
            UserResponse user = jdbcTemplate.queryForObject(
                    "CALL sp_login_user(?, ?)",
                    (rs, rowNum) -> new UserResponse(
                            rs.getLong("user_id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("role")
                    ),
                    normalizedUsernameOrEmail,
                    password
            );

            AuthResult result = createSession(user, ipAddress, userAgent);
            recordLoginActivity(user.getId(), normalizedUsernameOrEmail, "SUCCESS", null, ipAddress, userAgent);
            return result;
        } catch (EmptyResultDataAccessException e) {
            recordLoginActivity(null, normalizedUsernameOrEmail, "FAILED", "Invalid email or password.", ipAddress, userAgent);
            throw e;
        }
    }

    public AuthResult validateSession(String rawSessionToken) {
        String token = normalizeToken(rawSessionToken);
        String tokenHash = hashToken(token);

        jdbcTemplate.queryForObject(
                "CALL sp_validate_user_session(?)",
                (rs, rowNum) -> new UserResponse(
                        rs.getLong("user_id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("role")
                ),
                tokenHash
        );

        Timestamp refreshedExpiresAt = buildExpiresAt();

        return jdbcTemplate.queryForObject(
                "CALL sp_refresh_user_session(?, ?)",
                (rs, rowNum) -> new AuthResult(
                        new UserResponse(
                                rs.getLong("user_id"),
                                rs.getString("name"),
                                rs.getString("email"),
                                rs.getString("role")
                        ),
                        token,
                        rs.getTimestamp("expires_at")
                ),
                tokenHash,
                refreshedExpiresAt
        );
    }

    public AuthResult getCurrentSession(String rawSessionToken) {
        String token = normalizeToken(rawSessionToken);

        return jdbcTemplate.queryForObject(
                "CALL sp_validate_user_session(?)",
                (rs, rowNum) -> new AuthResult(
                        new UserResponse(
                                rs.getLong("user_id"),
                                rs.getString("name"),
                                rs.getString("email"),
                                rs.getString("role")
                        ),
                        token,
                        rs.getTimestamp("expires_at")
                ),
                hashToken(token)
        );
    }

    public void logout(String rawSessionToken) {
        String token = normalizeToken(rawSessionToken);

        jdbcTemplate.queryForObject(
                "CALL sp_logout_user_session(?)",
                Integer.class,
                hashToken(token)
        );
    }

    private AuthResult createSession(UserResponse user, String ipAddress, String userAgent) {
        String token = generateSessionToken();
        String tokenHash = hashToken(token);
        Timestamp expiresAt = buildExpiresAt();

        return jdbcTemplate.queryForObject(
                "CALL sp_create_user_session(?, ?, ?, ?, ?)",
                (rs, rowNum) -> new AuthResult(
                        new UserResponse(
                                rs.getLong("user_id"),
                                rs.getString("name"),
                                rs.getString("email"),
                                rs.getString("role")
                        ),
                        token,
                        rs.getTimestamp("expires_at")
                ),
                user.getId(),
                tokenHash,
                expiresAt,
                ipAddress,
                userAgent
        );
    }

    private void recordLoginActivity(Long userId,
                                     String usernameOrEmail,
                                     String loginStatus,
                                     String failureReason,
                                     String ipAddress,
                                     String userAgent) {
        jdbcTemplate.update(
                "CALL sp_record_login_activity(?, ?, ?, ?, ?, ?)",
                userId,
                usernameOrEmail,
                loginStatus,
                failureReason,
                ipAddress,
                userAgent
        );
    }

    private Timestamp buildExpiresAt() {
        long safeDuration = sessionDurationMinutes <= 0 ? 60 : sessionDurationMinutes;
        return Timestamp.from(Instant.now().plus(Duration.ofMinutes(safeDuration)));
    }

    private String generateSessionToken() {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String normalizeToken(String rawSessionToken) {
        if (rawSessionToken == null || rawSessionToken.isBlank()) {
            throw new IllegalArgumentException("Session token is required.");
        }

        return rawSessionToken.trim();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();

            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }

            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
