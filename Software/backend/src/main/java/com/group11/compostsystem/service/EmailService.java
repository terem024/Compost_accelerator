package com.group11.compostsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
public class EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);
    private static final URI RESEND_EMAIL_ENDPOINT = URI.create("https://api.resend.com/emails");

    private final JavaMailSender mailSender;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GmailApiEmailSender gmailApiEmailSender;
    private final Executor notificationExecutor;

    @Value("${app.email.provider:auto}")
    private String provider = "auto";

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.email.resend-api-key:}")
    private String resendApiKey;

    @Value("${app.email.from:}")
    private String resendFromEmail;

    @Value("${app.notification.email:}")
    private String notificationEmail;

    public EmailService(JavaMailSender mailSender, GmailApiEmailSender gmailApiEmailSender,
                        @Qualifier("notificationEmailExecutor") Executor notificationExecutor) {
        this.mailSender = mailSender;
        this.gmailApiEmailSender = gmailApiEmailSender;
        this.notificationExecutor = notificationExecutor;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        sendEmail(
                toEmail,
                "Reset your IoT Compost Accelerator password",
                """
                Hello,

                We received a request to reset your IoT Compost Accelerator account password.

                Open this link to set a new password:
                %s

                This link expires soon and can be used only once. If you did not request this, you can ignore this email.
                """.formatted(resetLink)
        );
    }

    public void sendRegistrationOtpEmail(String toEmail, String otp, long expirationMinutes) {
        sendEmail(
                toEmail,
                "Your IoT Compost Accelerator OTP",
                """
                Hello,

                Your OTP for creating an IoT Compost Accelerator account is:
                %s

                This code expires in %d minutes. If you did not request this, you can ignore this email.
                """.formatted(otp, expirationMinutes)
        );
    }

    public void sendActuatorActivationEmail(String actuatorName, String activationStatus, String timestamp,
                                           String sensorReadings) {
        if (notificationEmail == null || notificationEmail.isBlank()) {
            // No notification email configured, skip
            return;
        }

        String body = """
                Actuator Activation Notification

                Actuator: %s
                Status: %s
                Timestamp: %s

                Sensor Readings at Activation:
                %s

                This is an automated notification from your IoT Compost Accelerator system.
                """.formatted(actuatorName, activationStatus, timestamp, sensorReadings);
        // Optional alerts must never delay or prevent returning an actuator command to the ESP32.
        try {
            notificationExecutor.execute(() -> {
                try {
                    sendEmail(notificationEmail, "IoT Compost Accelerator - Actuator Activated", body);
                } catch (RuntimeException e) {
                    LOGGER.warn("Actuator notification could not be sent ({}).", e.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException e) {
            LOGGER.warn("Actuator notification skipped because the email queue is full or shutting down.");
        }
    }

    private void sendEmail(String toEmail, String subject, String body) {
        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalArgumentException("Recipient email address cannot be empty.");
        }

        switch (provider.trim().toLowerCase(Locale.ROOT)) {
            case "gmail-api" -> gmailApiEmailSender.send(toEmail, subject, body);
            case "resend" -> sendWithResend(toEmail, subject, body);
            case "smtp" -> sendWithSmtp(toEmail, subject, body);
            case "auto" -> {
                if (gmailApiEmailSender.hasConfiguration()) {
                    gmailApiEmailSender.send(toEmail, subject, body);
                } else if (resendApiKey != null && !resendApiKey.isBlank()) {
                    sendWithResend(toEmail, subject, body);
                } else {
                    sendWithSmtp(toEmail, subject, body);
                }
            }
            default -> throw new IllegalStateException("Unsupported EMAIL_PROVIDER configuration.");
        }
    }

    private void sendWithResend(String toEmail, String subject, String body) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY is not configured.");
        }
        if (resendFromEmail == null || resendFromEmail.isBlank()) {
            throw new IllegalStateException("RESEND_FROM_EMAIL is not configured.");
        }

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "from", resendFromEmail,
                    "to", new String[]{toEmail},
                    "subject", subject,
                    "text", body
            ));

            HttpRequest request = HttpRequest.newBuilder(RESEND_EMAIL_ENDPOINT)
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "compost-accelerator/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Email provider rejected the request with status " + response.statusCode()
                );
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to prepare the email request.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Email delivery was interrupted.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to connect to the email provider.", e);
        }
    }

    private void sendWithSmtp(String toEmail, String subject, String body) {
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException(
                    "Email is not configured. Set RESEND_API_KEY and RESEND_FROM_EMAIL, or configure Gmail SMTP."
            );
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
    }
}
