package com.group11.compostsystem.controller;

import com.group11.compostsystem.dto.ActuatorLogResponse;
import com.group11.compostsystem.dto.ActuatorStatusResponse;
import com.group11.compostsystem.dto.SensorReadingRequest;
import com.group11.compostsystem.dto.SensorReadingResponse;
import com.group11.compostsystem.dto.ActuatorLogResponse;
import com.group11.compostsystem.dto.ActuatorStatusResponse;
import com.group11.compostsystem.service.ActuatorLogService;
import com.group11.compostsystem.service.SensorReadingService;
import com.group11.compostsystem.service.ThresholdService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SensorController {

    private final SensorReadingService sensorReadingService;
    private final ThresholdService thresholdService;
    private final ActuatorLogService actuatorLogService;

    public SensorController(SensorReadingService sensorReadingService,
                            ThresholdService thresholdService,
                            ActuatorLogService actuatorLogService) {
        this.sensorReadingService = sensorReadingService;
        this.thresholdService = thresholdService;
        this.actuatorLogService = actuatorLogService;
    }

    @GetMapping("/actuator-status")
    public ResponseEntity<ActuatorStatusResponse> getActuatorStatus() {
        ActuatorStatusResponse response = actuatorLogService.getLatestActuatorStatus();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/actuator-logs")
    public ResponseEntity<List<ActuatorLogResponse>> getActuatorLogs() {
        List<ActuatorLogResponse> response = actuatorLogService.getActuatorLogHistory();
        return ResponseEntity.ok(response);
    }
}
