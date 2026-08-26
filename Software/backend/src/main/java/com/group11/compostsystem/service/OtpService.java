package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.OtpRequest;
import com.group11.compostsystem.dto.VerifyOtpRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmailService emailService;
    private final Map<String, OtpEntry> otpEntries = new ConcurrentHashMap<>();
    private final Map<String, Instant> verifiedEmails = new ConcurrentHashMap<>();

    @Value("${app.otp.expiration-minutes:10}")
    private long otpExpirationMinutes;

    public OtpService(EmailService emailService) {
        this.emailService = emailService;
    }

    public void sendRegistrationOtp(OtpRequest request) {
        String email = normalizeEmail(request == null ? null : request.getEmail());
        String otp = generateOtp();
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(getSafeExpirationMinutes()));

        otpEntries.put(email, new OtpEntry(otp, expiresAt));
        emailService.sendRegistrationOtpEmail(email, otp, getSafeExpirationMinutes());
    }

    public void verifyRegistrationOtp(VerifyOtpRequest request) {
        String email = normalizeEmail(request == null ? null : request.getEmail());
        String otp = request.getOtp() == null ? "" : request.getOtp().trim();

        if (otp.isBlank()) {
            throw new IllegalArgumentException("OTP is required.");
        }

        OtpEntry entry = otpEntries.get(email);

        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            otpEntries.remove(email);
            throw new IllegalArgumentException("OTP is invalid or expired.");
        }

        if (!entry.otp().equals(otp)) {
            throw new IllegalArgumentException("OTP is invalid or expired.");
        }

        otpEntries.remove(email);
        verifiedEmails.put(email, Instant.now().plus(Duration.ofMinutes(getSafeExpirationMinutes())));
    }

    public void consumeVerifiedEmail(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        Instant verifiedUntil = verifiedEmails.get(email);

        if (verifiedUntil == null || Instant.now().isAfter(verifiedUntil)) {
            verifiedEmails.remove(email);
            throw new IllegalArgumentException("Please verify the OTP sent to your email before creating an account.");
        }

        verifiedEmails.remove(email);
    }

    private String normalizeEmail(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();

        if (email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }

        if (!email.matches(EMAIL_PATTERN)) {
            throw new IllegalArgumentException("Email must be a valid address.");
        }

        return email;
    }

    private long getSafeExpirationMinutes() {
        return otpExpirationMinutes <= 0 ? 10 : otpExpirationMinutes;
    }

    private String generateOtp() {
        int otp = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("%06d", otp);
    }

    private record OtpEntry(String otp, Instant expiresAt) {
    }
}
