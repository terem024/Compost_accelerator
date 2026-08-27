package com.group11.compostsystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;

@Component
public class GmailApiEmailSender {

    private static final URI SEND_ENDPOINT = URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/send");
    private final String sender;
    private final GoogleCredentials credentials;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean configurationPresent;

    @Autowired
    public GmailApiEmailSender(
            @Value("${spring.mail.username:}") String sender,
            @Value("${app.email.gmail.client-id:}") String clientId,
            @Value("${app.email.gmail.client-secret:}") String clientSecret,
            @Value("${app.email.gmail.refresh-token:}") String refreshToken) {
        this(sender, createCredentials(clientId, clientSecret, refreshToken),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                !clientId.isBlank() || !clientSecret.isBlank() || !refreshToken.isBlank());
    }

    GmailApiEmailSender(String sender, GoogleCredentials credentials, HttpClient httpClient,
                        boolean configurationPresent) {
        this.sender = sender;
        this.credentials = credentials;
        this.httpClient = httpClient;
        this.configurationPresent = configurationPresent;
    }

    public boolean hasConfiguration() {
        return configurationPresent;
    }

    public void send(String recipient, String subject, String body) {
        if (sender == null || sender.isBlank() || credentials == null) {
            throw new IllegalStateException(
                    "Gmail API requires GMAIL_USERNAME, GMAIL_CLIENT_ID, GMAIL_CLIENT_SECRET and GMAIL_REFRESH_TOKEN.");
        }

        try {
            String raw = encodeMessage(sender, recipient, subject, body);
            // The official library caches access tokens and refreshes them when needed.
            credentials.refreshIfExpired();
            if (credentials.getAccessToken() == null) {
                throw new IllegalStateException("Gmail authorization did not return an access token.");
            }

            HttpRequest request = HttpRequest.newBuilder(SEND_ENDPOINT)
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + credentials.getAccessToken().getTokenValue())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(Map.of("raw", raw))))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Do not retry a send automatically: the first request may already have delivered it.
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Gmail API rejected delivery (HTTP " + response.statusCode()
                        + "). Check sender authorization, Gmail API access and sending quota.");
            }
        } catch (MessagingException e) {
            throw new IllegalStateException("Unable to prepare Gmail email. Check sender and recipient addresses.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gmail delivery was interrupted.");
        } catch (IOException e) {
            // OAuth and HTTP errors may contain credentials or email content; keep diagnostics safe.
            throw new IllegalStateException("Gmail authorization or delivery failed ("
                    + e.getClass().getSimpleName() + "). Check connectivity and reauthorize the sender if needed.");
        }
    }

    static String encodeMessage(String sender, String recipient, String subject, String body)
            throws MessagingException, IOException {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(mailbox(sender));
        message.setRecipient(Message.RecipientType.TO, mailbox(recipient));
        message.setSubject(subject, StandardCharsets.UTF_8.name());
        message.setText(body, StandardCharsets.UTF_8.name());
        message.saveChanges();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        message.writeTo(output);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
    }

    private static InternetAddress mailbox(String address) throws MessagingException {
        if (address == null || address.contains("\r") || address.contains("\n")) {
            throw new MessagingException("Invalid email address.");
        }
        InternetAddress mailbox = new InternetAddress(address.trim(), true);
        mailbox.validate();
        return mailbox;
    }

    private static GoogleCredentials createCredentials(String clientId, String clientSecret, String refreshToken) {
        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
            return null;
        }
        return UserCredentials.newBuilder()
                .setClientId(clientId.trim())
                .setClientSecret(clientSecret.trim())
                .setRefreshToken(refreshToken.trim())
                .build();
    }
}
