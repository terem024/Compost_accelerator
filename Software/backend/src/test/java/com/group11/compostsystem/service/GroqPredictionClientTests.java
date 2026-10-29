package com.group11.compostsystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Flow;

import static com.group11.compostsystem.service.GeminiPredictionClient.PredictionUnavailableException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GroqPredictionClientTests {
    private final HttpClient http = mock(HttpClient.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private GroqPredictionClient client(String key) {
        return new GroqPredictionClient(mapper, key, "openai/gpt-oss-120b", http);
    }

    @Test
    void missingKeyDoesNotSendRequest() {
        assertFalse(client(" ").isConfigured());
        assertThrows(PredictionUnavailableException.class, () -> client("").generate("snapshot"));
        verifyNoInteractions(http);
    }

    @Test
    @SuppressWarnings("unchecked")
    void successfulRequestUsesHeaderAuthStrictSchemaAndBoundedTimeout() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(mapper.writeValueAsString(Map.of("choices", java.util.List.of(
                Map.of("finish_reason", "stop", "message", Map.of("content", FallbackPredictionClientTests.VALID))))));
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        assertEquals(FallbackPredictionClientTests.VALID, client(" test-key ").generate("snapshot"));
        var captured = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captured.capture(), any(HttpResponse.BodyHandler.class));
        var request = captured.getValue();
        assertEquals("https://api.groq.com/openai/v1/chat/completions", request.uri().toString());
        assertEquals("Bearer test-key", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals(30, request.timeout().orElseThrow().toSeconds());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) {
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            public void onError(Throwable error) { fail(error); }
            public void onComplete() { }
        });
        var body = mapper.readTree(bytes.toString(StandardCharsets.UTF_8));
        assertEquals("openai/gpt-oss-120b", body.path("model").asText());
        assertEquals("snapshot", body.path("messages").path(0).path("content").asText());
        var format = body.path("response_format").path("json_schema");
        assertTrue(format.path("strict").asBoolean());
        assertFalse(format.path("schema").path("additionalProperties").asBoolean(true));
        assertEquals(7, format.path("schema").path("required").size());
        assertFalse(bytes.toString(StandardCharsets.UTF_8).contains("test-key"));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 413, 429, 500, 503})
    @SuppressWarnings("unchecked")
    void providerErrorsDoNotExposeSecretsOrRetry(int status) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var error = assertThrows(PredictionUnavailableException.class, () -> client("test-key").generate("snapshot"));
        assertFalse(error.getMessage().contains("test-key"));
        verify(response, never()).body();
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "not json", "{\"choices\":[{\"finish_reason\":\"length\"}]}",
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{}\",\"refusal\":\"refused\"}}]}"})
    @SuppressWarnings("unchecked")
    void incompleteOrMalformedEnvelopesAreRejected(String envelope) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(envelope);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        assertThrows(PredictionUnavailableException.class, () -> client("test-key").generate("snapshot"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void timeoutAndNetworkFailureAreSafe() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("secret"), new IOException("secret"));
        for (int i = 0; i < 2; i++) {
            var error = assertThrows(PredictionUnavailableException.class, () -> client("test-key").generate("snapshot"));
            assertFalse(error.getMessage().contains("secret"));
        }
    }
}
