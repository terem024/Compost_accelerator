package com.group11.compostsystem.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.notification.email:}")
    private String notificationEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("Gmail SMTP credentials are not configured.");
        }

        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalArgumentException("Recipient email address cannot be empty.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Reset your IoT Compost Accelerator password");
        message.setText("""
                Hello,

                We received a request to reset your IoT Compost Accelerator account password.

                Open this link to set a new password:
                %s

                This link expires soon and can be used only once. If you did not request this, you can ignore this email.
                """.formatted(resetLink));

        mailSender.send(message);
    }

    public void sendRegistrationOtpEmail(String toEmail, String otp, long expirationMinutes) {
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("Gmail SMTP credentials are not configured.");
        }

        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalArgumentException("Recipient email address cannot be empty.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Your IoT Compost Accelerator OTP");
        message.setText("""
                Hello,

                Your OTP for creating an IoT Compost Accelerator account is:
                %s

                This code expires in %d minutes. If you did not request this, you can ignore this email.
                """.formatted(otp, expirationMinutes));

        mailSender.send(message);
    }

    public void sendActuatorActivationEmail(String actuatorName, String activationStatus, String timestamp,
                                           String sensorReadings) {
        if (notificationEmail == null || notificationEmail.isBlank()) {
            // No notification email configured, skip
            return;
        }

        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("Gmail SMTP credentials are not configured.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(notificationEmail);
        message.setSubject("IoT Compost Accelerator - Actuator Activated");
        message.setText("""
                Actuator Activation Notification

                Actuator: %s
                Status: %s
                Timestamp: %s

                Sensor Readings at Activation:
                %s

                This is an automated notification from your IoT Compost Accelerator system.
                """.formatted(actuatorName, activationStatus, timestamp, sensorReadings));

        mailSender.send(message);
    }
}
