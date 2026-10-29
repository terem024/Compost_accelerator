package com.group11.compostsystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PredictionPayloadTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsCompleteJsonAndLegacyCodeFences() throws Exception {
        assertEquals("NEEDS_ATTENTION", PredictionPayload.parse(mapper,
                "```json\n" + FallbackPredictionClientTests.VALID + "\n```")
                .path("predicted_condition").asText());
    }

    @Test
    void rejectsWrongTypesMissingFieldsInvalidConditionsAndTrailingContent() {
        String valid = FallbackPredictionClientTests.VALID;
        for (String invalid : List.of(valid.replace("0.5", "\"0.5\""),
                valid.replace("0.5", "1.1"), valid.replace("NEEDS_ATTENTION", "READY"),
                valid.replace("\"estimated_days_remaining\":null", "\"estimated_days_remaining\":1.5"),
                valid.replace("\"estimated_ready_date\":null,", ""),
                valid.replace("Check the compost.", ""), valid + "{}")) {
            assertThrows(IOException.class, () -> PredictionPayload.parse(mapper, invalid));
        }
    }
}
