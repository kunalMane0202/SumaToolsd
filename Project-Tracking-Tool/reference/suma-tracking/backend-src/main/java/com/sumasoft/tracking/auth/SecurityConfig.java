package com.sumasoft.tracking.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * The security "front door":
 *  - Only POST /api/auth/login is open. EVERYTHING else needs a valid login token.
 *  - No cookies, no server memory sessions: the token in the Authorization header is checked every time.
 *    (Because no cookies are used, CSRF protection is not needed and is switched off.)
 *  - Passwords are hashed with BCrypt (strength 12).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthService authService) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, ex) ->
                        writeError(response, 401, "Unauthorized", "Please log in"))
                .accessDeniedHandler((request, response, ex) ->
                        writeError(response, 403, "Forbidden", "You do not have permission for this action")))
            .addFilterBefore(new SessionAuthenticationFilter(authService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Writes a short JSON error, in the same shape as ApiExceptionHandler. Messages are fixed text. */
    static void writeError(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status
                + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }
}