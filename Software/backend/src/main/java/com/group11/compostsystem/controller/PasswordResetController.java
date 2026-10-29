package com.group11.compostsystem.controller;

import com.group11.compostsystem.dto.ApiResponse;
import com.group11.compostsystem.dto.AuthResult;
import com.group11.compostsystem.dto.ForgotPasswordRequest;
import com.group11.compostsystem.dto.ResetPasswordRequest;
import com.group11.compostsystem.service.PasswordResetService;
import com.group11.compostsystem.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetController.class);

    private final PasswordResetService passwordResetService;
    private final AuthService authService;

    public PasswordResetController(PasswordResetService passwordResetService, AuthService authService) {
        this.passwordResetService = passwordResetService;
        this.authService = authService;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse> forgotPassword(@RequestBody ForgotPasswordRequest request,
                                                      HttpServletRequest servletRequest) {
        try {
            passwordResetService.requestPasswordReset(
                    request,
                    getClientIp(servletRequest),
                    servletRequest.getHeader("User-Agent")
            );

            return ResponseEntity.ok(
                    new ApiResponse(true, PasswordResetService.FORGOT_PASSWORD_MESSAGE)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    new ApiResponse(false, e.getMessage())
            );
        } catch (DataAccessException e) {
            LOGGER.warn("Password reset request could not be completed because of a database error.", e);
            return ResponseEntity.status(503).body(
                    new ApiResponse(false, "We couldn't process your password reset request. Please try again shortly.")
            );
        } catch (RuntimeException e) {
            LOGGER.warn("Password reset request could not send the reset email.", e);
            return ResponseEntity.status(503).body(
                    new ApiResponse(false, "We couldn't send the password reset email right now. Please try again shortly.")
            );
        }
    }

    @PostMapping("/password-reset-email")
    public ResponseEntity<ApiResponse> sendOwnResetEmail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest servletRequest) {
        AuthResult session;
        try {
            String token = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                    ? authorization.substring(7).trim() : "";
            if (token.isBlank()) throw new IllegalArgumentException("Missing session");
            session = authService.getCurrentSession(token);
        } catch (IllegalArgumentException | EmptyResultDataAccessException e) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Your session has expired. Please log in again."));
        } catch (DataAccessException e) {
            LOGGER.warn("Could not validate the password reset email session.", e);
            return ResponseEntity.status(503).body(new ApiResponse(false, "We couldn't process your password reset request. Please try again shortly."));
        }
        try {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            // Only the server-validated account may receive a confirmed-send response.
            request.setEmail(session.getUser().getEmail());
            boolean sent = passwordResetService.requestPasswordReset(request,
                    getClientIp(servletRequest), servletRequest.getHeader("User-Agent"));
            if (!sent) return ResponseEntity.status(401).body(
                    new ApiResponse(false, "Your account could not be found. Please log in again."));
            return ResponseEntity.ok(new ApiResponse(true, PasswordResetService.RESET_EMAIL_SENT_MESSAGE));
        } catch (RuntimeException e) {
            LOGGER.warn("Signed-in password reset email request failed.", e);
            return ResponseEntity.status(503).body(new ApiResponse(false,
                    "We couldn't send your password reset email. Please try again shortly. If this continues, contact the system administrator."));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            passwordResetService.resetPassword(request);
            return ResponseEntity.ok(
                    new ApiResponse(true, PasswordResetService.RESET_SUCCESS_MESSAGE)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    new ApiResponse(false, e.getMessage())
            );
        } catch (DataAccessException e) {
            LOGGER.warn("Password reset could not be completed because of a database error.", e);
            return ResponseEntity.status(503).body(
                    new ApiResponse(false, "We couldn't reset your password right now. Please try again shortly.")
            );
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}
