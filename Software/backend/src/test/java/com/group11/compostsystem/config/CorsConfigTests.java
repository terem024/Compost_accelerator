package com.group11.compostsystem.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigTests {

    @Test
    void allowsPatchRequestsFromConfiguredFrontend() {
        CorsConfig config = new CorsConfig("https://iot-compost-accelerator.up.railway.app");
        TestCorsRegistry registry = new TestCorsRegistry();

        config.addCorsMappings(registry);

        CorsConfiguration cors = registry.configurations().get("/api/**");
        assertTrue(cors.getAllowedOrigins().contains("https://iot-compost-accelerator.up.railway.app"));
        assertTrue(cors.getAllowedMethods().contains("PATCH"));
    }

    private static class TestCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
