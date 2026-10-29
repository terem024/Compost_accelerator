package com.group11.compostsystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.group11.compostsystem.service.GeminiPredictionClient.PredictionUnavailableException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FallbackPredictionClientTests {
    static final String VALID = """
            {"predicted_condition":"NEEDS_ATTENTION","prediction_summary":"Check the compost.",
             "estimated_ready_date":null,"estimated_days_remaining":null,
             "recommendation":"Inspect before use.","trend_summary":"No measured trend available.",
             "confidence_score":0.5}
            """;
    private final GeminiPredictionClient gemini = mock(GeminiPredictionClient.class);
    private final GroqPredictionClient groq = mock(GroqPredictionClient.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private FallbackPredictionClient client(boolean enabled) {
        return new FallbackPredictionClient(gemini, groq, mapper, enabled);
    }

    private void backupAvailable() {
        when(groq.isConfigured()).thenReturn(true);
        when(groq.generate("snapshot")).thenReturn(VALID);
        when(groq.modelName()).thenReturn("openai/gpt-oss-120b");
    }

    @Test
    void successfulPrimaryNeverCallsBackup() {
        when(gemini.generate("snapshot")).thenReturn(VALID);
        when(gemini.modelName()).thenReturn("gemini-2.5-flash");
        var result = client(true).generate("snapshot");
        assertEquals("Gemini", result.provider());
        assertEquals("gemini-2.5-flash", result.model());
        verifyNoInteractions(groq);
    }

    @Test
    void providerErrorUsesBackupOnceWithSamePromptAndRecordsItsModel() {
        when(gemini.generate("snapshot")).thenThrow(new PredictionUnavailableException("quota"));
        backupAvailable();
        var result = client(true).generate("snapshot");
        assertEquals("Groq", result.provider());
        assertEquals("openai/gpt-oss-120b", result.model());
        assertEquals(VALID, result.raw());
        assertEquals("NEEDS_ATTENTION", result.json().path("predicted_condition").asText());
        var order = inOrder(gemini, groq);
        order.verify(gemini).generate("snapshot");
        order.verify(groq).isConfigured();
        order.verify(groq).generate("snapshot");
        verify(gemini, times(1)).generate("snapshot");
        verify(groq, times(1)).generate("snapshot");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{}", "null", "[]", "{\"prediction_summary\":\"partial\"}"})
    void malformedPrimaryFallsBack(String invalid) {
        when(gemini.generate("snapshot")).thenReturn(invalid);
        backupAvailable();
        assertEquals("Groq", client(true).generate("snapshot").provider());
    }

    @Test
    void missingKeyPreservesPrimaryErrorAndNeverAttemptsBackup() {
        when(gemini.generate("snapshot")).thenThrow(new PredictionUnavailableException("quota"));
        assertEquals("quota", assertThrows(PredictionUnavailableException.class,
                () -> client(true).generate("snapshot")).getMessage());
        verify(groq, never()).generate(anyString());
    }

    @Test
    void disabledBackupIsNeverContacted() {
        when(gemini.generate("snapshot")).thenThrow(new PredictionUnavailableException("quota"));
        assertThrows(PredictionUnavailableException.class, () -> client(false).generate("snapshot"));
        verifyNoInteractions(groq);
    }

    @Test
    void bothFailuresAreSafeAndDoNotReturnAFabricatedPrediction() {
        when(gemini.generate("snapshot")).thenThrow(new PredictionUnavailableException("secret primary"));
        when(groq.isConfigured()).thenReturn(true);
        when(groq.generate("snapshot")).thenThrow(new PredictionUnavailableException("secret backup"));
        var error = assertThrows(PredictionUnavailableException.class, () -> client(true).generate("snapshot"));
        assertTrue(error.getMessage().contains("Both AI services"));
        assertTrue(error.getMessage().contains("daily prediction has not been used"));
        assertFalse(error.getMessage().contains("secret"));
    }

    @Test
    void invalidBackupIsRejected() {
        when(gemini.generate("snapshot")).thenReturn("{}");
        when(groq.isConfigured()).thenReturn(true);
        when(groq.generate("snapshot")).thenReturn(VALID.replace("0.5", "5"));
        assertThrows(PredictionUnavailableException.class, () -> client(true).generate("snapshot"));
    }

    @Test
    void interruptedRequestDoesNotStartBackupAndKeepsInterruptFlag() {
        when(gemini.generate("snapshot")).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new PredictionUnavailableException("interrupted");
        });
        try {
            assertThrows(PredictionUnavailableException.class, () -> client(true).generate("snapshot"));
            assertTrue(Thread.currentThread().isInterrupted());
            verifyNoInteractions(groq);
        } finally {
            Thread.interrupted();
        }
    }
}
