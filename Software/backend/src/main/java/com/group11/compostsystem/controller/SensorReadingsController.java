package com.group11.compostsystem.controller;

import com.group11.compostsystem.dto.SensorReadingRequest;
import com.group11.compostsystem.dto.SensorReadingResponse;
import com.group11.compostsystem.service.SensorReadingService;
import com.group11.compostsystem.service.SensorSseService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sensor-readings")
public class SensorReadingsController {

    private final SensorReadingService sensorReadingService;
    private final SensorSseService sensorSseService;

    public SensorReadingsController(SensorReadingService sensorReadingService,
                                    SensorSseService sensorSseService) {
        this.sensorReadingService = sensorReadingService;
        this.sensorSseService = sensorSseService;
    }

    @PostMapping
    public ResponseEntity<SensorReadingResponse> saveSensorReading(@RequestBody SensorReadingRequest request) {
        SensorReadingResponse response = sensorReadingService.saveSensorReading(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/latest")
    public ResponseEntity<SensorReadingResponse> getLatestSensorReading() {
        try {
            SensorReadingResponse response = sensorReadingService.getLatestSensorReading();
            return ResponseEntity.ok(response);
        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<SensorReadingResponse>> getSensorReadings() {
        List<SensorReadingResponse> response = sensorReadingService.getAllSensorReadings();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stream")
    public SseEmitter streamSensorReadings() {
        return sensorSseService.subscribe();
    }
}
