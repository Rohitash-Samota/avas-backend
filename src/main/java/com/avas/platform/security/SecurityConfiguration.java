package com.avas.platform.security;

import com.avas.platform.auth.JwtAuthenticationFilter;
import com.avas.platform.auth.OAuthFailureHandler;
import com.avas.platform.auth.OAuthSuccessHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    FilterRegistrationBean<ActiveRoleFilter> activeRoleFilterRegistration(ActiveRoleFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwt,
            ActiveRoleFilter activeRole, ObjectProvider<ClientRegistrationRepository> registrations,
            OAuthSuccessHandler successHandler, OAuthFailureHandler failureHandler,
            ApiSecurityErrorHandler securityErrors) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(securityErrors)
                        .accessDeniedHandler(securityErrors))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/", "/api/v1", "/api/v1/status").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1", "/api/v1/status").permitAll()
                        .requestMatchers("/actuator/**", "/error", "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/login/mobile", "/api/v1/auth/refresh", "/api/v1/auth/providers").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/commerce/products").permitAll()
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        .requestMatchers("/api/v1/admin/roles/**", "/api/v1/admin/users/*/roles/**").hasAuthority("ROLE_MANAGE")
                        .requestMatchers("/api/v1/admin/users/**").hasAuthority("USER_MANAGE")
                        .requestMatchers("/api/v1/admin/knowledge/**").hasAuthority("KNOWLEDGE_MANAGE")
                        .requestMatchers("/api/v1/admin/system/**").hasAuthority("AUDIT_READ")
                        .requestMatchers("/api/v1/admin/configuration/**").hasAuthority("PLATFORM_CONFIG_MANAGE")
                        .requestMatchers("/api/v1/admin/models/**").hasAuthority("MODEL_MANAGE")
                        .requestMatchers("/api/v1/admin/pricing/**").hasAuthority("PRICE_MANAGE")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/pricing/submissions").hasAnyAuthority("PRICE_SUBMIT", "PRICE_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/pricing/submissions", "/api/v1/pricing/submissions/mine")
                                .hasAnyAuthority("PRICE_SUBMIT", "PRICE_MANAGE", "BUDGET_RECOMMEND")
                        .requestMatchers("/api/v1/pricing/budget-recommendations/**").hasAuthority("BUDGET_RECOMMEND")
                        .requestMatchers(HttpMethod.POST, "/api/v1/commerce/checkout").hasAuthority("COMMERCE_BUY")
                        .requestMatchers(HttpMethod.POST, "/api/v1/commerce/wallet/topups").hasAuthority("COMMERCE_BUY")
                        .requestMatchers(HttpMethod.GET, "/api/v1/commerce/wallet/**").hasAuthority("WALLET_READ")
                        .requestMatchers("/api/v1/commerce/orders", "/api/v1/commerce/orders/**", "/api/v1/commerce/payments/**")
                                .hasAnyAuthority("COMMERCE_BUY", "PAYMENT_MANAGE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/projects").hasAuthority("PROJECT_CREATE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/projects/**").hasAuthority("PROJECT_UPDATE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/projects/*/drawings/generate").hasAuthority("DRAWING_GENERATE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/projects/*/estimates/generate").hasAuthority("ESTIMATE_READ")
                        .requestMatchers(HttpMethod.POST, "/api/v1/drawings/*/feedback").hasAuthority("DRAWING_FEEDBACK")
                        .requestMatchers(HttpMethod.POST, "/api/v1/drawings/*/regenerate").hasAuthority("DRAWING_GENERATE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/drawings/*/approve-concept").hasAuthority("DRAWING_APPROVE_CONCEPT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/estimates/*/approve").hasAuthority("ESTIMATE_APPROVE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/projects/**").hasAnyAuthority("PROJECT_UPDATE", "DRAWING_GENERATE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/projects/**", "/api/v1/drawings/**", "/api/v1/estimates/**")
                                .hasAnyAuthority("PROJECT_READ", "PROJECT_ELIGIBLE_READ", "DRAWING_REVIEW",
                                        "ASSIGNED_PROJECT_READ", "PROJECT_MANAGE")
                        .requestMatchers("/api/v1/knowledge/**").hasAuthority("KNOWLEDGE_MANAGE")
                        .anyRequest().authenticated())
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(activeRole, JwtAuthenticationFilter.class);
        if (registrations.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth.successHandler(successHandler).failureHandler(failureHandler));
        }
        return http.build();
    }
}
