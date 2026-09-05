package com.group11.compostsystem.service;

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
