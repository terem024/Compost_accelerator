package com.group11.compostsystem.controller;

import com.group11.compostsystem.dto.ApiResponse;
import com.group11.compostsystem.dto.ForgotPasswordRequest;
import com.group11.compostsystem.dto.ResetPasswordRequest;
import com.group11.compostsystem.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetController.class);

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
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
            return ResponseEntity.ok(
                    new ApiResponse(true, PasswordResetService.FORGOT_PASSWORD_MESSAGE)
            );
        } catch (RuntimeException e) {
            LOGGER.warn("Password reset request could not send the reset email.", e);
            return ResponseEntity.status(503).body(
                    new ApiResponse(false, "We couldn't send the password reset email right now. Please try again shortly.")
            );
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
