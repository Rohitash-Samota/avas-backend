package com.avas.platform.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuthSuccessHandler implements AuthenticationSuccessHandler {
    private final AuthService auth;
    private final AuthCookie cookies;
    private final String frontendUrl;

    OAuthSuccessHandler(AuthService auth, AuthCookie cookies,
            @Value("${avas.auth.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.auth = auth;
        this.cookies = cookies;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        var user = (OAuth2User) authentication.getPrincipal();
        var issued = auth.oauthUser(user.getAttribute("email"), user.getAttribute("given_name"), user.getAttribute("family_name"));
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.create(issued.refreshToken()).toString());
        response.sendRedirect(frontendUrl + "/oauth2/redirect?token=" +
                URLEncoder.encode(issued.response().accessToken(), StandardCharsets.UTF_8));
    }
}
