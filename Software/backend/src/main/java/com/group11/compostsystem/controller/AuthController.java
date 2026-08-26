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
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://127.0.0.1:5173"
})
public class AuthController {

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
            otpService.consumeVerifiedEmail(request == null ? null : request.getEmail());
            AuthResult result = authService.register(
                    request,
                    getClientIp(servletRequest),
                    servletRequest.getHeader("User-Agent")
            );
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
        } catch (DataAccessException e) {
            return ResponseEntity.badRequest().body(
                    new AuthResponse(false, "Registration failed: " + e.getMostSpecificCause().getMessage(), null)
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
            return ResponseEntity.badRequest().body(
                    new ApiResponse(false, "Unable to send OTP email. Check the backend email configuration.")
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
            return ResponseEntity.badRequest().body(
                    new AuthResponse(false, "Login failed: " + e.getMostSpecificCause().getMessage(), null)
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
            return ResponseEntity.badRequest().body(
                    new AuthResponse(false, e.getMessage(), null)
            );
        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.status(401).body(
                    new AuthResponse(false, "Session is expired or invalid.", null)
            );
        } catch (DataAccessException e) {
            return ResponseEntity.status(401).body(
                    new AuthResponse(false, "Session validation failed.", null)
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
            return ResponseEntity.badRequest().body(
                    new AuthResponse(false, "Logout failed: " + e.getMostSpecificCause().getMessage(), null)
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
