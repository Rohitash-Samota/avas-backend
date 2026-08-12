package com.avas.platform.auth;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, UserResponse user) {
}
