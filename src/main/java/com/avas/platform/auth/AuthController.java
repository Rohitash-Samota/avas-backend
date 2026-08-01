package com.avas.platform.auth;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

import static com.avas.platform.auth.AuthModels.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    private final AuthCookie cookies;
    private final boolean googleEnabled;

    AuthController(AuthService service, AuthCookie cookies,
            @Value("${avas.auth.google.enabled:false}") boolean googleEnabled) {
        this.service = service;
        this.cookies = cookies;
        this.googleEnabled = googleEnabled;
    }

    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withCookie(service.register(request));
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withCookie(service.login(request));
    }

    @PostMapping("/login/mobile")
    ResponseEntity<AuthResponse> loginMobile(@Valid @RequestBody MobileLoginRequest request) {
        return withCookie(service.loginMobile(request));
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(@CookieValue(name = AuthCookie.NAME, required = false) String refreshToken) {
        return withCookie(service.rotate(refreshToken));
    }

    @PostMapping("/logout")
    ResponseEntity<Map<String, String>> logout(
            @CookieValue(name = AuthCookie.NAME, required = false) String refreshToken) {
        service.logout(refreshToken);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .body(Map.of("status", "SIGNED_OUT"));
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal AvasPrincipal principal) {
        return service.user(principal.userId());
    }

    @GetMapping("/providers")
    ProviderStatus providers() {
        return new ProviderStatus(true, googleEnabled,
                googleEnabled ? "/oauth2/authorization/google" : null);
    }

    private ResponseEntity<AuthResponse> withCookie(IssuedTokens issued) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.create(issued.refreshToken()).toString())
                .body(issued.response());
    }
}
