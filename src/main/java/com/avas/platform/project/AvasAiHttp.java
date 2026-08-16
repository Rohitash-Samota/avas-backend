package com.avas.platform.project;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Builds the HTTP client every call to the AVAS AI service boundary goes through.
 *
 * <p>Pinned to HTTP/1.1 deliberately. The JDK client negotiates HTTP/2 by default, and over
 * cleartext that means an {@code h2c} upgrade request the AI service's ASGI server rejects with a
 * bare 400. Each client treats a transport failure as "the reader is unavailable" and falls back to
 * its deterministic path, so the negotiation mismatch is invisible in the product: readings simply
 * never use the model. Protocol selection therefore belongs here, once, rather than in each
 * client.</p>
 */
final class AvasAiHttp {
    private AvasAiHttp() {
    }

    /** A template whose connect and read timeouts are both the caller's configured budget. */
    static RestTemplate client(RestTemplateBuilder builder, long timeoutSeconds) {
        var timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build());
        factory.setReadTimeout(timeout);
        return builder.requestFactory(() -> factory).build();
    }
}
