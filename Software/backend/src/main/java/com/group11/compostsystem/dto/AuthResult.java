package com.group11.compostsystem.dto;

import java.sql.Timestamp;

public class AuthResult {
    private final UserResponse user;
    private final String sessionToken;
    private final Timestamp expiresAt;

    public AuthResult(UserResponse user, String sessionToken, Timestamp expiresAt) {
        this.user = user;
        this.sessionToken = sessionToken;
        this.expiresAt = expiresAt;
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
