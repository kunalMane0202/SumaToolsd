package com.sumasoft.tracking.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Runs before every request:
 * reads "Authorization: Bearer <token>", checks the token, and records who is calling.
 * If the person still has a temporary password, they may ONLY change it (or log out / see "me").
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_WHILE_PASSWORD_MUST_CHANGE =
            Set.of("/api/auth/me", "/api/auth/change-password", "/api/auth/logout");

    private final AuthService authService;

    public SessionAuthenticationFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            Optional<AuthenticatedUser> caller = authService.authenticateToken(token);
            if (caller.isPresent()) {
                AuthenticatedUser user = caller.get();
                if (user.mustChangePassword()
                        && !ALLOWED_WHILE_PASSWORD_MUST_CHANGE.contains(request.getRequestURI())) {
                    SecurityConfig.writeError(response, 403, "Forbidden",
                            "You must change your temporary password first (POST /api/auth/change-password)");
                    return;
                }
                UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.roleCode())));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            }
        }
        chain.doFilter(request, response);
    }
}