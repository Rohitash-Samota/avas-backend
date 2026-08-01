package com.avas.platform.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final String[] frontendOrigins;

    public WebConfiguration(@Value("${avas.auth.frontend-url:http://localhost:3000}") String frontendUrl) {
        if (frontendUrl.startsWith("http://localhost:")) {
            this.frontendOrigins = new String[] { frontendUrl, frontendUrl.replace("localhost", "127.0.0.1") };
        } else if (frontendUrl.startsWith("http://127.0.0.1:")) {
            this.frontendOrigins = new String[] { frontendUrl, frontendUrl.replace("127.0.0.1", "localhost") };
        } else {
            this.frontendOrigins = new String[] { frontendUrl };
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(frontendOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowedHeaders("Content-Type", "Authorization", "X-Active-Role", "Idempotency-Key", "X-Correlation-Id")
                .exposedHeaders("X-Correlation-Id")
                .allowCredentials(true);
    }
}
