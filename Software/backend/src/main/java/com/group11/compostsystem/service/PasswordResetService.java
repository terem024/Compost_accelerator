package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.ForgotPasswordRequest;
import com.group11.compostsystem.dto.ResetPasswordRequest;
import com.group11.compostsystem.dto.UserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
            "Email has been sent.";
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
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();

        if (email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }

        if (!email.matches(EMAIL_PATTERN)) {
            throw new IllegalArgumentException("Email must be a valid address.");
        }

        try {
            UserResponse user = jdbcTemplate.queryForObject(
                    "CALL sp_get_user_by_email(?)",
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

            jdbcTemplate.query(
                    "CALL sp_create_password_reset_token(?, ?, ?, ?, ?)",
                    rs -> null,
                    user.getId(),
                    tokenHash,
                    expiresAt,
                    ipAddress,
                    userAgent
            );

            emailService.sendPasswordResetEmail(user.getEmail(), buildResetLink(rawToken));
        } catch (EmptyResultDataAccessException ignored) {
            // Keep the response generic so callers cannot enumerate accounts.
        }
    }

    public void resetPassword(ResetPasswordRequest request) {
        String token = request.getToken() == null ? "" : request.getToken().trim();
        String newPassword = request.getNewPassword();
        String confirmPassword = request.getConfirmPassword();

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
            user = jdbcTemplate.queryForObject(
                    "CALL sp_validate_password_reset_token(?)",
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

        Integer passwordRows = jdbcTemplate.queryForObject(
                "CALL sp_update_user_password(?, ?, ?)",
                Integer.class,
                user.getId(),
                passwordHash,
                salt
        );

        if (passwordRows == null || passwordRows == 0) {
            throw new IllegalArgumentException("Unable to reset password for this account.");
        }

        jdbcTemplate.queryForObject(
                "CALL sp_mark_password_reset_token_used(?)",
                Integer.class,
                tokenHash
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
