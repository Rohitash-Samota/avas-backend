package com.avas.platform.auth;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
class AuthCookie {
    static final String NAME = "avas_refresh";
    private final Duration lifetime;

    AuthCookie(@Value("${avas.auth.refresh-token-days:30}") long days) {
        this.lifetime = Duration.ofDays(days);
    }

    ResponseCookie create(String token) {
        return ResponseCookie.from(NAME, token).httpOnly(true).secure(false).sameSite("Lax")
                .path("/api/v1/auth").maxAge(lifetime).build();
    }

    ResponseCookie clear() {
        return ResponseCookie.from(NAME, "").httpOnly(true).secure(false).sameSite("Lax")
                .path("/api/v1/auth").maxAge(Duration.ZERO).build();
    }
}
