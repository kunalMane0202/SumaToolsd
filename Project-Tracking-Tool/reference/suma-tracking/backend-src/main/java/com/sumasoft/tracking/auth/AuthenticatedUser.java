package com.sumasoft.tracking.auth;

/**
 * "Who is calling right now": filled in from the login token on every request.
 * It never contains the password or the token itself.
 */
public record AuthenticatedUser(
        Long userId,
        Long sessionId,
        String email,
        String fullName,
        String roleCode,
        String userGroup,
        boolean mustChangePassword
) {

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(roleCode);
    }
}