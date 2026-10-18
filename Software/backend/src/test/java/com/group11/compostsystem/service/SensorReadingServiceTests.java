package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.SensorReadingRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SensorReadingServiceTests {

    private final SensorReadingService service = new SensorReadingService(
            null, null, null, null, null
    );

    @Test
    void acceptsPartialSensorValues() {
        assertDoesNotThrow(() -> service.validateSensorValues(
                new BigDecimal("55.00"), new BigDecimal("20.00"), null, null
        ));
    }

    @Test
    void usesTheFirstMoistureSensorWhenTheSecondIsDisabled() {
        SensorReadingRequest request = new SensorReadingRequest();
        request.setMoisturePercent1(new BigDecimal("63.40"));

        assertEquals(new BigDecimal("63.40"), request.getEffectiveMoistureLevel());
    }

    @Test
    void usesTheSecondMoistureSensorWhenTheFirstIsDisabled() {
        SensorReadingRequest request = new SensorReadingRequest();
        request.setMoisturePercent2(new BigDecimal("57.20"));

        assertEquals(new BigDecimal("57.20"), request.getEffectiveMoistureLevel());
    }

    @Test
    void rejectsPayloadWhenEverySensorValueIsMissing() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.validateSensorValues(null, null, null, null)
        );

        assertEquals("At least one sensor value is required.", exception.getMessage());
    }

    @Test
    void missingSensorDoesNotReceiveAFalseStatus() {
        assertNull(service.statusFor(
                null, new BigDecimal("40.00"), new BigDecimal("70.00")
        ));
    }
}
