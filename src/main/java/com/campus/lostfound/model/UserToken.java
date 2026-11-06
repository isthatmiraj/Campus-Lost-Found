package com.campus.lostfound.model;

import java.time.LocalDateTime;

public class UserToken {
    private final String id;
    private final String userId;
    private final String tokenHash;
    private final LocalDateTime createdAt;
    private final LocalDateTime loginStartedAt;
    private final LocalDateTime expiresAt;

    public UserToken(String id, String userId, String tokenHash, LocalDateTime createdAt,
                     LocalDateTime loginStartedAt, LocalDateTime expiresAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.loginStartedAt = loginStartedAt;
        this.expiresAt = expiresAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLoginStartedAt() { return loginStartedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
