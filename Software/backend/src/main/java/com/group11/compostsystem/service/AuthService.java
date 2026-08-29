package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.AuthResult;
import com.group11.compostsystem.dto.LoginRequest;
import com.group11.compostsystem.dto.RegisterRequest;
import com.group11.compostsystem.dto.UserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public AuthResult register(RegisterRequest request, String ipAddress, String userAgent) {
        validateRegisterRequest(request);

        String name = request.getName() == null ? "" : request.getName().trim();
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        String password = request.getPassword();

        String salt = generateSalt();
        String passwordHash = sha256Hex(salt + password);

        jdbcTemplate.update(
                """
                INSERT INTO users (full_name, username, password_hash, password_salt, role)
                VALUES (?, ?, ?, ?, 'OPERATOR')
                """,
                name,
                email,
                passwordHash,
                salt
        );

        UserResponse user = jdbcTemplate.queryForObject(
                """
                SELECT user_id, full_name AS name, username AS email, role
                FROM users
                WHERE username = ?
                """,
                (rs, rowNum) -> new UserResponse(
                        rs.getLong("user_id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("role")
                ),
                email
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

        jdbcTemplate.update(
                """
                INSERT INTO user_sessions
                    (user_id, session_token_hash, expires_at, ip_address, user_agent, status)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                """,
                user.getId(),
                tokenHash,
                expiresAt,
                truncate(ipAddress, 45),
                truncate(userAgent, 255)
        );

        return new AuthResult(user, token, expiresAt);
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

    private String generateSalt() {
        byte[] saltBytes = new byte[32];
        SECURE_RANDOM.nextBytes(saltBytes);
        return toHex(saltBytes);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String normalizeToken(String rawSessionToken) {
        if (rawSessionToken == null || rawSessionToken.isBlank()) {
            throw new IllegalArgumentException("Session token is required.");
        }

        return rawSessionToken.trim();
    }

    private String hashToken(String token) {
        return sha256Hex(token);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
