package com.group11.compostsystem.controller;

import com.group11.compostsystem.dto.SensorReadingRequest;
import com.group11.compostsystem.service.NoActiveBatchException;
import com.group11.compostsystem.service.SensorReadingService;
import com.group11.compostsystem.service.SensorSseService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SensorReadingsControllerTests {

    @Test
    void noActiveBatchIsReportedAsConflictInsteadOfServerError() {
        SensorReadingService sensorReadingService = mock(SensorReadingService.class);
        SensorReadingRequest request = new SensorReadingRequest();
        when(sensorReadingService.saveSensorReading(request)).thenThrow(
                new NoActiveBatchException("No active compost batch found.")
        );
        SensorReadingsController controller = new SensorReadingsController(
                sensorReadingService, mock(SensorSseService.class)
        );

        ResponseEntity<?> response = controller.saveSensorReading(request);

        assertEquals(409, response.getStatusCode().value());
        assertEquals(
                "No active compost batch found.",
                ((Map<?, ?>) response.getBody()).get("message")
        );
    }
}
