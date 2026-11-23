package com.sumasoft.tracking.auth;

import com.sumasoft.tracking.auth.AuthDtos.ChangePasswordRequest;
import com.sumasoft.tracking.auth.AuthDtos.LoginRequest;
import com.sumasoft.tracking.auth.AuthDtos.LoginResponse;
import com.sumasoft.tracking.auth.AuthDtos.MeResponse;
import com.sumasoft.tracking.auth.AuthDtos.MessageResponse;
import com.sumasoft.tracking.auth.AuthDtos.ResetPasswordRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web addresses (API) for login.
 *   POST /api/auth/login                  -> email + password, returns a token (no login needed)
 *   POST /api/auth/logout                 -> ends this session
 *   GET  /api/auth/me                     -> who am I
 *   POST /api/auth/change-password        -> current + new password
 *   POST /api/users/{id}/reset-password   -> Super Admin sets a temporary password
 *   POST /api/users/{id}/revoke-access    -> Super Admin logs someone out everywhere
 * Every other address needs the header:  Authorization: Bearer <token>
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }

    @PostMapping("/auth/logout")
    public MessageResponse logout(@AuthenticationPrincipal AuthenticatedUser caller, HttpServletRequest http) {
        return authService.logout(caller, http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }

    @GetMapping("/auth/me")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser caller) {
        return authService.me(caller);
    }

    @PostMapping("/auth/change-password")
    public MessageResponse changePassword(@AuthenticationPrincipal AuthenticatedUser caller,
                                          @Valid @RequestBody ChangePasswordRequest request,
                                          HttpServletRequest http) {
        return authService.changePassword(caller, request, http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }

    @PostMapping("/users/{id}/reset-password")
    public MessageResponse resetPassword(@AuthenticationPrincipal AuthenticatedUser caller,
                                         @PathVariable Long id,
                                         @Valid @RequestBody ResetPasswordRequest request,
                                         HttpServletRequest http) {
        return authService.resetPassword(caller, id, request, http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }

    @PostMapping("/users/{id}/revoke-access")
    public MessageResponse revokeAccess(@AuthenticationPrincipal AuthenticatedUser caller,
                                        @PathVariable Long id,
                                        HttpServletRequest http) {
        return authService.revokeAccess(caller, id, http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }
}