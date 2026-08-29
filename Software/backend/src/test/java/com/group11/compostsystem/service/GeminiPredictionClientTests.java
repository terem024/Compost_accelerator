package com.group11.compostsystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeminiPredictionClientTests {
    private final HttpClient http = mock(HttpClient.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private GeminiPredictionClient client(String key) {
        return new GeminiPredictionClient(mapper, key, "gemini-2.5-flash", http);
    }

    @Test
    void missingKeyDoesNotSendARequest() {
        var error = assertThrows(GeminiPredictionClient.PredictionUnavailableException.class,
                () -> client(" ").generate("snapshot"));
        assertTrue(error.getMessage().contains("administrator"));
        verifyNoInteractions(http);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsKeyInHeaderWithBoundedTimeoutAndReadsAllTextParts() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":["
                + "{\"text\":\"private reasoning\",\"thought\":true},"
                + "{\"text\":\"first part\"},{\"text\":\" second part\"}]}}]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        assertEquals("first part second part", client(" test-key ").generate("snapshot"));
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("test-key", request.getValue().headers().firstValue("x-goog-api-key").orElseThrow());
        assertNull(request.getValue().uri().getQuery());
        assertEquals(60, request.getValue().timeout().orElseThrow().toSeconds());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 429, 500, 503})
    @SuppressWarnings("unchecked")
    void providerErrorsAreSafeAndDoNotRetry(int status) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn("secret diagnostic");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var error = assertThrows(GeminiPredictionClient.PredictionUnavailableException.class,
                () -> client("test-key").generate("snapshot"));
        assertFalse(error.getMessage().contains("secret"));
        assertFalse(error.getMessage().contains("test-key"));
        if (status == 429) assertTrue(error.getMessage().contains("usage limit"));
        verify(response, never()).body();
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void timeoutIsReportedWithoutExposingRequestDetails() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("secret"));
        var error = assertThrows(GeminiPredictionClient.PredictionUnavailableException.class,
                () -> client("test-key").generate("snapshot"));
        assertTrue(error.getMessage().contains("taking longer"));
        assertFalse(error.getMessage().contains("secret"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void incompleteResponseIsNotUsedAsAPrediction() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        assertThrows(GeminiPredictionClient.PredictionUnavailableException.class,
                () -> client("test-key").generate("snapshot"));
    }
}
