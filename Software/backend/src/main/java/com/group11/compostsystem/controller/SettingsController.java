package com.group11.compostsystem.controller;

import com.group11.compostsystem.dto.AuthResult;
import com.group11.compostsystem.dto.ThresholdSettingsRequest;
import com.group11.compostsystem.dto.ThresholdSettingsResponse;
import com.group11.compostsystem.service.AuthService;
import com.group11.compostsystem.service.ThresholdService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://127.0.0.1:5173"
})
public class SettingsController {

    private final ThresholdService thresholdService;
    private final AuthService authService;

    public SettingsController(ThresholdService thresholdService, AuthService authService) {
        this.thresholdService = thresholdService;
        this.authService = authService;
    }

    @GetMapping("/thresholds")
    public ResponseEntity<ThresholdSettingsResponse> getThresholdSettings() {
        ThresholdSettingsResponse response = thresholdService.getThresholdSettings();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/thresholds")
    public ResponseEntity<?> saveThresholdSettings(@RequestBody ThresholdSettingsRequest request,
                                                   @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        try {
            AuthResult session = authService.getCurrentSession(extractBearerToken(authorizationHeader));
            ThresholdSettingsResponse response = thresholdService.saveThresholdSettings(
                    request,
                    session.getUser().getId().intValue()
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.status(401).body(error("Session is expired or invalid."));
        } catch (DataAccessException e) {
            return ResponseEntity.badRequest().body(error(e.getMostSpecificCause().getMessage()));
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

    private Map<String, Object> error(String message) {
        return Map.of(
                "success", false,
                "message", message
        );
    }
}
