package com.group11.compostsystem.dto;

import java.sql.Timestamp;

public class AuthResponse {
    private boolean success;
    private String message;
    private UserResponse user;
    private String sessionToken;
    private Timestamp expiresAt;

    public AuthResponse(boolean success, String message, UserResponse user) {
        this.success = success;
        this.message = message;
        this.user = user;
    }

    public AuthResponse(boolean success, String message, UserResponse user, String sessionToken, Timestamp expiresAt) {
        this.success = success;
        this.message = message;
        this.user = user;
        this.sessionToken = sessionToken;
        this.expiresAt = expiresAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public UserResponse getUser() {
        return user;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public Timestamp getExpiresAt() {
        return expiresAt;
    }
}
