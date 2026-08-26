package com.group11.compostsystem.controller;

import com.group11.compostsystem.dto.SensorConnectionLogResponse;
import com.group11.compostsystem.dto.SensorConnectionStatusResponse;
import com.group11.compostsystem.service.SensorConnectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sensor-connection")
public class SensorConnectionController {

    private final SensorConnectionService sensorConnectionService;

    public SensorConnectionController(SensorConnectionService sensorConnectionService) {
        this.sensorConnectionService = sensorConnectionService;
    }

    @GetMapping("/status")
    public ResponseEntity<SensorConnectionStatusResponse> getStatus() {
        return ResponseEntity.ok(sensorConnectionService.getStatus());
    }

    @GetMapping("/logs")
    public ResponseEntity<List<SensorConnectionLogResponse>> getLogs() {
        return ResponseEntity.ok(sensorConnectionService.getHistory());
    }
}
