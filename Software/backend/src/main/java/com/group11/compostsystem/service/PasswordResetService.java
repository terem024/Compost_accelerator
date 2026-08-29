package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.ForgotPasswordRequest;
import com.group11.compostsystem.dto.ResetPasswordRequest;
import com.group11.compostsystem.dto.UserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class PasswordResetService {

    public static final String FORGOT_PASSWORD_MESSAGE =
            "If an account exists for that email, a password reset link has been sent.";
    public static final String RESET_SUCCESS_MESSAGE =
            "Password reset successful. You may now log in.";

    private static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.password-reset.expiration-minutes:30}")
    private long resetTokenExpirationMinutes;

    public PasswordResetService(JdbcTemplate jdbcTemplate, EmailService emailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailService = emailService;
    }

    public void requestPasswordReset(ForgotPasswordRequest request, String ipAddress, String userAgent) {
        String email = request == null || request.getEmail() == null
                ? ""
                : request.getEmail().trim().toLowerCase();

        if (email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }

        if (!email.matches(EMAIL_PATTERN)) {
            throw new IllegalArgumentException("Email must be a valid address.");
        }

        try {
            UserResponse user = jdbcTemplate.queryForObject(
                    """
                    SELECT user_id, full_name AS name, username AS email, role
                    FROM users
                    WHERE username = ?
                    LIMIT 1
                    """,
                    (rs, rowNum) -> new UserResponse(
                            rs.getLong("user_id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("role")
                    ),
                    email
            );

            String rawToken = generateToken();
            String tokenHash = sha256(rawToken);
            Timestamp expiresAt = buildExpiresAt();

            jdbcTemplate.update(
                    "UPDATE password_reset_tokens SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND expires_at <= CURRENT_TIMESTAMP"
            );
            jdbcTemplate.update(
                    "UPDATE password_reset_tokens SET status = 'EXPIRED' WHERE user_id = ? AND status = 'ACTIVE'",
                    user.getId()
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO password_reset_tokens
                        (user_id, token_hash, expires_at, request_ip, user_agent, status)
                    VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                    """,
                    user.getId(),
                    tokenHash,
                    expiresAt,
                    truncate(ipAddress, 45),
                    truncate(userAgent, 255)
            );

            try {
                emailService.sendPasswordResetEmail(user.getEmail(), buildResetLink(rawToken));
            } catch (RuntimeException e) {
                jdbcTemplate.update(
                        "UPDATE password_reset_tokens SET status = 'EXPIRED' WHERE token_hash = ? AND status = 'ACTIVE'",
                        tokenHash
                );
                throw e;
            }
        } catch (EmptyResultDataAccessException ignored) {
            // Keep the response generic so callers cannot enumerate accounts.
        }
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String token = request == null || request.getToken() == null ? "" : request.getToken().trim();
        String newPassword = request == null ? null : request.getNewPassword();
        String confirmPassword = request == null ? null : request.getConfirmPassword();

        if (token.isBlank()) {
            throw new IllegalArgumentException("Reset token is required.");
        }

        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("New password is required.");
        }

        if (newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }

        if (confirmPassword == null || !newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Confirm password must match the new password.");
        }

        String tokenHash = sha256(token);

        UserResponse user;
        try {
            jdbcTemplate.update(
                    "UPDATE password_reset_tokens SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND expires_at <= CURRENT_TIMESTAMP"
            );
            user = jdbcTemplate.queryForObject(
                    """
                    SELECT u.user_id, u.full_name AS name, u.username AS email, u.role
                    FROM password_reset_tokens prt
                    JOIN users u ON u.user_id = prt.user_id
                    WHERE prt.token_hash = ? AND prt.status = 'ACTIVE'
                      AND prt.used_at IS NULL AND prt.expires_at > CURRENT_TIMESTAMP
                    LIMIT 1
                    """,
                    (rs, rowNum) -> new UserResponse(
                            rs.getLong("user_id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("role")
                    ),
                    tokenHash
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Reset link is invalid, expired, or already used.");
        }

        String salt = generateSalt();
        String passwordHash = sha256(salt + newPassword);

        int passwordRows = jdbcTemplate.update(
                "UPDATE users SET password_hash = ?, password_salt = ? WHERE user_id = ?",
                passwordHash,
                salt,
                user.getId()
        );

        if (passwordRows != 1) {
            throw new IllegalArgumentException("Unable to reset password for this account.");
        }

        int tokenRows = jdbcTemplate.update(
                """
                UPDATE password_reset_tokens SET status = 'USED', used_at = CURRENT_TIMESTAMP
                WHERE token_hash = ? AND status = 'ACTIVE' AND used_at IS NULL
                """,
                tokenHash
        );
        if (tokenRows != 1) throw new IllegalArgumentException("Reset link is invalid, expired, or already used.");
        jdbcTemplate.update(
                "UPDATE user_sessions SET status = 'REVOKED', revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND status = 'ACTIVE'",
                user.getId()
        );
    }

    private Timestamp buildExpiresAt() {
        long safeDuration = resetTokenExpirationMinutes <= 0 ? 30 : resetTokenExpirationMinutes;
        return Timestamp.from(Instant.now().plus(Duration.ofMinutes(safeDuration)));
    }

    private String buildResetLink(String rawToken) {
        String baseUrl = frontendUrl == null || frontendUrl.isBlank()
                ? "http://localhost:5173"
                : frontendUrl.replaceAll("/+$", "");
        String encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        return baseUrl + "/reset-password?token=" + encodedToken;
    }

    private String generateToken() {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private String generateSalt() {
        byte[] saltBytes = new byte[32];
        SECURE_RANDOM.nextBytes(saltBytes);
        return toHex(saltBytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();

        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }

        return builder.toString();
    }
}
