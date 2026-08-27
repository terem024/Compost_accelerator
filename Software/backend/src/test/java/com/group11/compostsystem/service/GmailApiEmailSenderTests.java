package com.group11.compostsystem.service;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GmailApiEmailSenderTests {

    @Test
    void encodesUtf8MessageWithTheOriginalRecipientAndContent() throws Exception {
        String raw = GmailApiEmailSender.encodeMessage("sender@gmail.com", "student@g.batstate-u.edu.ph",
                "Verification code", "Your code is 123456. Temperature: 30\u00b0C.");
        assertFalse(raw.contains("="));
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(Base64.getUrlDecoder().decode(raw)));
        assertEquals("sender@gmail.com", message.getFrom()[0].toString());
        assertEquals("student@g.batstate-u.edu.ph", message.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("Verification code", message.getSubject());
        assertTrue(message.getContent().toString().contains("123456. Temperature: 30\u00b0C."));
    }

    @Test
    void rejectsAddressHeaderInjection() {
        assertThrows(MessagingException.class, () -> GmailApiEmailSender.encodeMessage(
                "sender@gmail.com", "student@gmail.com\r\nBcc: other@example.com", "OTP", "123456"));
    }

    @Test
    void missingConfigurationDoesNotCallGoogle() {
        HttpClient client = mock(HttpClient.class);
        GmailApiEmailSender sender = new GmailApiEmailSender("sender@gmail.com", null, client, true);
        assertThrows(IllegalStateException.class, () -> sender.send("user@gmail.com", "OTP", "123456"));
        verifyNoInteractions(client);
    }

    @Test
    @SuppressWarnings("unchecked")
    void refreshesCredentialsAndSendsOverHttps() throws Exception {
        GoogleCredentials credentials = mock(GoogleCredentials.class);
        when(credentials.getAccessToken()).thenReturn(new AccessToken("test-access-token", new Date()));
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        GmailApiEmailSender sender = new GmailApiEmailSender("sender@gmail.com", credentials, client, true);

        sender.send("user@gmail.com", "OTP", "123456");

        verify(credentials).refreshIfExpired();
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("https://gmail.googleapis.com/gmail/v1/users/me/messages/send", request.getValue().uri().toString());
        assertEquals("Bearer test-access-token", request.getValue().headers().firstValue("Authorization").orElseThrow());
        assertEquals(10, request.getValue().timeout().orElseThrow().toSeconds());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectedSendDoesNotRetryOrExposeTheProviderResponse() throws Exception {
        GoogleCredentials credentials = mock(GoogleCredentials.class);
        when(credentials.getAccessToken()).thenReturn(new AccessToken("test-access-token", new Date()));
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(403);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        GmailApiEmailSender sender = new GmailApiEmailSender("sender@gmail.com", credentials, client, true);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> sender.send("user@gmail.com", "OTP", "123456"));

        assertTrue(error.getMessage().contains("403"));
        verify(response, never()).body();
        verify(client, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void authorizationFailureDoesNotExposeSecretsOrAttemptDelivery() throws Exception {
        GoogleCredentials credentials = mock(GoogleCredentials.class);
        doThrow(new IOException("secret-refresh-token")).when(credentials).refreshIfExpired();
        HttpClient client = mock(HttpClient.class);
        GmailApiEmailSender sender = new GmailApiEmailSender("sender@gmail.com", credentials, client, true);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> sender.send("user@gmail.com", "OTP", "123456"));

        assertFalse(error.getMessage().contains("secret-refresh-token"));
        assertNull(error.getCause());
        verifyNoInteractions(client);
    }
}
