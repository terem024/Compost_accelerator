package com.group11.compostsystem.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailServiceTests {

    @Test
    void gmailProviderSendsOtpAndResetLinkToTheRequestingUser() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        GmailApiEmailSender gmail = mock(GmailApiEmailSender.class);
        EmailService service = new EmailService(smtp, gmail, Runnable::run);
        ReflectionTestUtils.setField(service, "provider", "gmail-api");
        ReflectionTestUtils.setField(service, "resendApiKey", "old-resend-key");

        service.sendRegistrationOtpEmail("student@g.batstate-u.edu.ph", "123456", 10);
        service.sendPasswordResetEmail("user@gmail.com", "https://example.com/reset-password?token=test");

        verify(gmail).send(eq("student@g.batstate-u.edu.ph"), anyString(), contains("123456"));
        verify(gmail).send(eq("user@gmail.com"), anyString(), contains("https://example.com/reset-password?token=test"));
        verifyNoInteractions(smtp);
    }

    @Test
    void explicitGmailFailureDoesNotSilentlyFallBackToOtherProviders() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        GmailApiEmailSender gmail = mock(GmailApiEmailSender.class);
        EmailService service = new EmailService(smtp, gmail, Runnable::run);
        ReflectionTestUtils.setField(service, "provider", "gmail-api");
        doThrow(new IllegalStateException("Not authorized")).when(gmail).send(anyString(), anyString(), anyString());
        assertThrows(IllegalStateException.class,
                () -> service.sendRegistrationOtpEmail("user@gmail.com", "123456", 10));
        verifyNoInteractions(smtp);
    }

    @Test
    void notificationRunsOutsideTheHardwareRequestAndFailureDoesNotEscape() {
        GmailApiEmailSender gmail = mock(GmailApiEmailSender.class);
        List<Runnable> queued = new ArrayList<>();
        EmailService service = new EmailService(mock(JavaMailSender.class), gmail, queued::add,
                Clock.fixed(Instant.parse("2026-11-04T04:00:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "provider", "gmail-api");
        ReflectionTestUtils.setField(service, "notificationEmail", "operator@gmail.com");
        doThrow(new IllegalStateException("Provider unavailable")).when(gmail).send(anyString(), anyString(), anyString());

        service.sendActuatorActivationEmail("FAN", "ON", "2026-08-27", "Gas: 60%");

        verifyNoInteractions(gmail);
        assertEquals(1, queued.size());
        assertDoesNotThrow(() -> queued.get(0).run());
        verify(gmail).send(eq("operator@gmail.com"), anyString(), contains("Gas: 60%"));
    }

    @Test
    void fullNotificationQueueDoesNotInterruptActuatorControl() {
        EmailService service = new EmailService(mock(JavaMailSender.class), mock(GmailApiEmailSender.class),
                task -> { throw new RejectedExecutionException(); },
                Clock.fixed(Instant.parse("2026-11-04T04:00:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "notificationEmail", "operator@gmail.com");
        assertDoesNotThrow(() -> service.sendActuatorActivationEmail("FAN", "ON", "today", "Gas: 60%"));
    }

    @Test
    void unconfiguredNotificationRecipientDoesNotQueueEmail() {
        List<Runnable> queued = new ArrayList<>();
        EmailService service = new EmailService(mock(JavaMailSender.class), mock(GmailApiEmailSender.class), queued::add);
        service.sendActuatorActivationEmail("WATER_SPRAY", "ON", "today", "Moisture: 17%");
        assertTrue(queued.isEmpty());
    }

    @Test
    void actuatorNotificationFallsBackToTheConfiguredGmailSender() {
        GmailApiEmailSender gmail = mock(GmailApiEmailSender.class);
        EmailService service = new EmailService(mock(JavaMailSender.class), gmail, Runnable::run,
                Clock.fixed(Instant.parse("2026-11-04T04:00:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "provider", "gmail-api");
        ReflectionTestUtils.setField(service, "fromEmail", "compostaccelerator@gmail.com");

        service.sendActuatorActivationEmail("WATER_SPRAY", "ON", "today", "Moisture: 17%");

        verify(gmail).send(
                eq("compostaccelerator@gmail.com"),
                contains("Actuator Activated"),
                contains("Moisture: 17%")
        );
    }

    @Test
    void quietHoursBlockSprayAlertsButNotOtpOrPasswordResetEmails() {
        GmailApiEmailSender gmail = mock(GmailApiEmailSender.class);
        EmailService service = new EmailService(mock(JavaMailSender.class), gmail, Runnable::run,
                Clock.fixed(Instant.parse("2026-11-04T16:00:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "provider", "gmail-api");
        ReflectionTestUtils.setField(service, "notificationEmail", "operator@example.com");
        service.sendActuatorActivationEmail("WATER_SPRAY", "ON", "today", "Moisture: 17%");
        service.sendRegistrationOtpEmail("user@example.com", "123456", 10);
        service.sendPasswordResetEmail("user@example.com", "https://example.com/reset");
        verify(gmail, never()).send(eq("operator@example.com"), anyString(), anyString());
        verify(gmail).send(eq("user@example.com"), contains("OTP"), contains("123456"));
        verify(gmail).send(eq("user@example.com"), contains("Reset your"), contains("https://example.com/reset"));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-04T09:59:59Z, true",
            "2026-11-04T10:00:00Z, false",
            "2026-11-04T16:00:00Z, false",
            "2026-11-05T00:29:59Z, false",
            "2026-11-05T00:30:59Z, false",
            "2026-11-05T00:31:00Z, true"
    })
    void fanEmailFollowsPhilippineTimeWindow(String utcTime, boolean sends) {
        GmailApiEmailSender gmail = mock(GmailApiEmailSender.class);
        List<Runnable> queued = new ArrayList<>();
        EmailService service = new EmailService(mock(JavaMailSender.class), gmail, queued::add,
                Clock.fixed(Instant.parse(utcTime), ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "provider", "gmail-api");
        ReflectionTestUtils.setField(service, "notificationEmail", "operator@example.com");
        service.sendActuatorActivationEmail("FAN", "ON", "today", "Humidity: 100%");
        assertEquals(sends ? 1 : 0, queued.size());
        queued.forEach(Runnable::run);
        verify(gmail, times(sends ? 1 : 0)).send(anyString(), anyString(), anyString());
    }

    @Test
    void localSmtpRemainsAvailable() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        GmailApiEmailSender gmail = mock(GmailApiEmailSender.class);
        EmailService service = new EmailService(smtp, gmail, Runnable::run);
        ReflectionTestUtils.setField(service, "provider", "smtp");
        ReflectionTestUtils.setField(service, "fromEmail", "sender@gmail.com");
        service.sendRegistrationOtpEmail("user@gmail.com", "123456", 10);
        verify(smtp).send(any(SimpleMailMessage.class));
        verifyNoInteractions(gmail);
    }
}
