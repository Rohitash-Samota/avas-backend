package com.avas.platform.security;

import com.avas.platform.auth.AvasPrincipal;
import com.avas.platform.auth.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.stream.Stream;

@Component
public class ActiveRoleFilter extends OncePerRequestFilter {
    public static final String ACTIVE_ROLE = "avas.activeRole";
    public static final String ACTIVE_PERMISSIONS = "avas.activePermissions";
    private final AuthService auth;

    public ActiveRoleFilter(AuthService auth) { this.auth = auth; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var requestedRole = request.getHeader("X-Active-Role");
        if (requestedRole != null) requestedRole = requestedRole.trim().toUpperCase();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) correlationId = UUID.randomUUID().toString();
        response.setHeader("X-Correlation-Id", correlationId);

        if (authentication != null && authentication.getPrincipal() instanceof AvasPrincipal principal) {
            var authBootstrap = request.getRequestURI().startsWith("/api/v1/auth/");
            var role = requestedRole == null || requestedRole.isBlank()
                    ? auth.firstAssignedActiveRole(principal.userId())
                    : auth.assignedActiveRole(principal.userId(), requestedRole);
            if (role.isEmpty() && authBootstrap) role = auth.firstAssignedActiveRole(principal.userId());
            if (role.isEmpty()) {
                reject(response);
                return;
            }
            var active = role.get();
            request.setAttribute(ACTIVE_ROLE, active.code());
            request.setAttribute(ACTIVE_PERMISSIONS, active.permissions());
            response.setHeader("X-Active-Role", active.code());
            if (!authBootstrap) {
                var authorities = Stream.concat(
                        Stream.of(new SimpleGrantedAuthority("ROLE_" + active.code())),
                        active.permissions().stream().map(SimpleGrantedAuthority::new)).toList();
                var selectedRole = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                selectedRole.setDetails(authentication.getDetails());
                SecurityContextHolder.getContext().setAuthentication(selectedRole);
            }
        } else {
            request.setAttribute(ACTIVE_ROLE, requestedRole == null || requestedRole.isBlank() ? "INDIVIDUAL" : requestedRole);
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":403,\"code\":\"ACTIVE_ROLE_NOT_ASSIGNED\",\"message\":\"The selected role is not active or assigned to this account. Choose one of the account's available roles.\"}");
    }
}
