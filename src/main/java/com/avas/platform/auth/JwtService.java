package com.avas.platform.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
class JwtService {
    private final SecretKey key;
    private final String issuer;
    private final Duration accessLifetime;

    JwtService(@Value("${avas.auth.jwt-secret}") String secret,
            @Value("${avas.auth.issuer}") String issuer,
            @Value("${avas.auth.access-token-minutes:15}") long accessMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        if (accessMinutes < 1 || accessMinutes > 60) {
            throw new IllegalStateException("ACCESS_TOKEN_MINUTES must be between 1 and 60");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessLifetime = Duration.ofMinutes(accessMinutes);
    }

    String issue(UserEntity user) {
        var now = Instant.now();
        var builder = Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim("tenant", user.getTenantId())
                .claim("username", user.getUsername())
                .claim("roles", user.getRoles().stream().map(RoleEntity::getCode).sorted().toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessLifetime)));
        if (user.getEmail() != null) builder.claim("email", user.getEmail());
        return builder.signWith(key).compact();
    }

    AvasPrincipal parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                .parseSignedClaims(token).getPayload();
        List<?> rolesValue = claims.get("roles", List.class);
        Set<String> roles = rolesValue == null ? Set.of() : rolesValue.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new AvasPrincipal(UUID.fromString(claims.getSubject()), String.valueOf(claims.get("tenant")),
                claims.get("email", String.class), roles);
    }

    long expiresInSeconds() { return accessLifetime.toSeconds(); }
}
