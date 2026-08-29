package com.group11.compostsystem.controller;

import com.group11.compostsystem.dto.AuthResult;
import com.group11.compostsystem.dto.AuthResponse;
import com.group11.compostsystem.dto.ApiResponse;
import com.group11.compostsystem.dto.LoginRequest;
import com.group11.compostsystem.dto.OtpRequest;
import com.group11.compostsystem.dto.RegisterRequest;
import com.group11.compostsystem.dto.VerifyOtpRequest;
import com.group11.compostsystem.service.AuthService;
import com.group11.compostsystem.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final OtpService otpService;

    public AuthController(AuthService authService, OtpService otpService) {
        this.authService = authService;
        this.otpService = otpService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request,
                                                 HttpServletRequest servletRequest) {
        try {
            authService.validateRegisterRequest(request);
            otpService.requireVerifiedEmail(request == null ? null : request.getEmail());
            AuthResult result = authService.register(
                    request,
                    getClientIp(servletRequest),
                    servletRequest.getHeader("User-Agent")
            );
            otpService.consumeVerifiedEmail(request.getEmail());
            return ResponseEntity.ok(
                    new AuthResponse(
                            true,
                            "Registration successful.",
                            result.getUser(),
                            result.getSessionToken(),
                            result.getExpiresAt()
                    )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    new AuthResponse(false, e.getMessage(), null)
            );
        } catch (DuplicateKeyException e) {
            return ResponseEntity.badRequest().body(
                    new AuthResponse(false, "Email is already registered.", null)
            );
        } catch (DataAccessException e) {
            LOGGER.warn("Registration could not be completed because of a database error.", e);
            return ResponseEntity.status(503).body(
                    new AuthResponse(false, "We couldn't create your account right now. Please try again shortly.", null)
            );
        }
    }

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse> sendOtp(@RequestBody OtpRequest request) {
        try {
            otpService.sendRegistrationOtp(request);
            return ResponseEntity.ok(
                    new ApiResponse(true, "OTP sent to your email.")
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    new ApiResponse(false, e.getMessage())
            );
        } catch (RuntimeException e) {
            LOGGER.warn("Registration OTP email could not be sent.", e);
            return ResponseEntity.status(503).body(
                    new ApiResponse(false, "We couldn't send the verification code right now. Please try again shortly.")
            );
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse> verifyOtp(@RequestBody VerifyOtpRequest request) {
        try {
            otpService.verifyRegistrationOtp(request);
            return ResponseEntity.ok(
                    new ApiResponse(true, "OTP verified.")
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    new ApiResponse(false, e.getMessage())
            );
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request,
                                              HttpServletRequest servletRequest) {
        try {
            AuthResult result = authService.login(
                    request,
                    getClientIp(servletRequest),
                    servletRequest.getHeader("User-Agent")
            );
            return ResponseEntity.ok(
                    new AuthResponse(
                            true,
                            "Login successful.",
                            result.getUser(),
                            result.getSessionToken(),
                            result.getExpiresAt()
                    )
            );
        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.status(401).body(
                    new AuthResponse(false, "Invalid email or password.", null)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    new AuthResponse(false, e.getMessage(), null)
            );
        } catch (DataAccessException e) {
            LOGGER.warn("Login could not be completed because of a database error.", e);
            return ResponseEntity.status(503).body(
                    new AuthResponse(false, "We couldn't sign you in right now. Please try again shortly.", null)
            );
        }
    }

    @GetMapping("/session")
    public ResponseEntity<AuthResponse> getSession(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        try {
            AuthResult result = authService.validateSession(extractBearerToken(authorizationHeader));
            return ResponseEntity.ok(
                    new AuthResponse(
                            true,
                            "Session is valid.",
                            result.getUser(),
                            result.getSessionToken(),
                            result.getExpiresAt()
                    )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(
                    new AuthResponse(false, e.getMessage(), null)
            );
        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.status(401).body(
                    new AuthResponse(false, "Session is expired or invalid.", null)
            );
        } catch (DataAccessException e) {
            LOGGER.warn("Session validation is temporarily unavailable because of a database error.", e);
            return ResponseEntity.status(503).body(
                    new AuthResponse(false, "We couldn't check your session right now. Please try again shortly.", null)
            );
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        try {
            authService.logout(extractBearerToken(authorizationHeader));
            return ResponseEntity.ok(
                    new AuthResponse(true, "Logout successful.", null)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    new AuthResponse(false, e.getMessage(), null)
            );
        } catch (DataAccessException e) {
            LOGGER.warn("Logout could not be completed because of a database error.", e);
            return ResponseEntity.status(503).body(
                    new AuthResponse(false, "We couldn't sign you out normally, but your session has been cleared.", null)
            );
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new IllegalArgumentException("Session token is required.");
        }

        if (authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorizationHeader.substring(7).trim();
        }

        return authorizationHeader.trim();
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}
