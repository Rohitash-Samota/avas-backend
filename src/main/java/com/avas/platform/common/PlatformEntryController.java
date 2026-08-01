package com.avas.platform.common;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PlatformEntryController {
    private final URI frontendUri;

    PlatformEntryController(@Value("${avas.auth.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.frontendUri = URI.create(frontendUrl);
    }

    @GetMapping("/")
    ResponseEntity<Void> openWebApplication() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, frontendUri.toString())
                .build();
    }
}
