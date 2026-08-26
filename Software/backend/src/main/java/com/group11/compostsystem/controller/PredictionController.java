package com.group11.compostsystem.controller;

import com.group11.compostsystem.dto.AIPredictionRequest;
import com.group11.compostsystem.dto.AIPredictionAvailabilityResponse;
import com.group11.compostsystem.dto.AIPredictionResponse;
import com.group11.compostsystem.service.PredictionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @GetMapping("/test")
    public String testPredictionController() {
        return "Prediction controller is working.";
    }

    @PostMapping("/generate")
    public AIPredictionResponse generatePrediction(@RequestBody(required = false) AIPredictionRequest request) {
        return predictionService.generatePrediction(request == null ? null : request.getBatchId());
    }

    @PostMapping("/generate/{batchId}")
    public AIPredictionResponse generatePredictionByBatchId(
            @PathVariable Integer batchId
    ) {
        return predictionService.generatePrediction(batchId);
    }

    @GetMapping("/availability/{batchId}")
    public AIPredictionAvailabilityResponse getPredictionAvailability(@PathVariable Integer batchId) {
        return predictionService.getPredictionAvailability(batchId);
    }
}
