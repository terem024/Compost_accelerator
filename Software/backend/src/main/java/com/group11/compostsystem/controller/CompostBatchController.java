package com.group11.compostsystem.controller;

import com.group11.compostsystem.dto.AuthResult;
import com.group11.compostsystem.dto.CompostBatchRequest;
import com.group11.compostsystem.dto.CompostBatchResponse;
import com.group11.compostsystem.dto.CompostBatchStatusRequest;
import com.group11.compostsystem.service.AuthService;
import com.group11.compostsystem.service.CompostBatchService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/compost-batches")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://127.0.0.1:5173"
})
public class CompostBatchController {

    private final CompostBatchService compostBatchService;
    private final AuthService authService;

    public CompostBatchController(CompostBatchService compostBatchService, AuthService authService) {
        this.compostBatchService = compostBatchService;
        this.authService = authService;
    }

    @GetMapping("/active")
    public ResponseEntity<?> getActiveBatch() {
        CompostBatchResponse activeBatch = compostBatchService.getActiveBatch();

        if (activeBatch == null) {
            return ResponseEntity.status(404).body(error("No active compost batch found."));
        }

        return ResponseEntity.ok(activeBatch);
    }

    @GetMapping
    public ResponseEntity<List<CompostBatchResponse>> getBatches() {
        return ResponseEntity.ok(compostBatchService.getBatches());
    }

    @GetMapping("/{batchId}")
    public ResponseEntity<?> getBatchById(@PathVariable Integer batchId) {
        try {
            return ResponseEntity.ok(compostBatchService.getBatchById(batchId));
        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.status(404).body(error("Compost batch was not found."));
        }
    }

    @PostMapping
    public ResponseEntity<?> createBatch(@RequestBody CompostBatchRequest request,
                                         @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        try {
            Integer userId = resolveUserId(authorizationHeader);
            CompostBatchResponse response = compostBatchService.createBatch(request, userId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.status(401).body(error("Session is expired or invalid."));
        } catch (DataAccessException e) {
            return ResponseEntity.badRequest().body(error(e.getMostSpecificCause().getMessage()));
        }
    }

    @PutMapping("/{batchId}")
    public ResponseEntity<?> updateBatch(@PathVariable Integer batchId,
                                         @RequestBody CompostBatchRequest request,
                                         @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        try {
            Integer userId = resolveUserId(authorizationHeader);
            return ResponseEntity.ok(compostBatchService.updateBatch(batchId, request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.status(404).body(error("Compost batch or session was not found."));
        } catch (DataAccessException e) {
            return ResponseEntity.badRequest().body(error(e.getMostSpecificCause().getMessage()));
        }
    }

    @PostMapping("/{batchId}/activate")
    public ResponseEntity<?> setActiveBatch(@PathVariable Integer batchId,
                                            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        try {
            resolveUserId(authorizationHeader);
            return ResponseEntity.ok(compostBatchService.setActiveBatch(batchId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.status(404).body(error("Compost batch or session was not found."));
        } catch (DataAccessException e) {
            return ResponseEntity.badRequest().body(error(e.getMostSpecificCause().getMessage()));
        }
    }

    @PatchMapping("/{batchId}/status")
    public ResponseEntity<?> updateBatchStatus(@PathVariable Integer batchId,
                                               @RequestBody CompostBatchStatusRequest request,
                                               @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        try {
            resolveUserId(authorizationHeader);
            return ResponseEntity.ok(compostBatchService.updateBatchStatus(batchId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.status(404).body(error("Compost batch or session was not found."));
        } catch (DataAccessException e) {
            return ResponseEntity.badRequest().body(error(e.getMostSpecificCause().getMessage()));
        }
    }

    private Integer resolveUserId(String authorizationHeader) {
        AuthResult session = authService.getCurrentSession(extractBearerToken(authorizationHeader));
        return session.getUser().getId().intValue();
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
