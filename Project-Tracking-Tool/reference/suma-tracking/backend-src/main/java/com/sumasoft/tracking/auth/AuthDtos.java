package com.sumasoft.tracking.auth;

import com.sumasoft.tracking.user.AppUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * The small "message shapes" used by the login API, kept together in one file.
 * Passwords are only ever received, never sent back.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email is not valid")
            @Size(max = 254, message = "Email is too long")
            String email,

            @NotBlank(message = "Password is required")
            @Size(max = 200, message = "Password is too long")
            String password
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required")
            @Size(max = 200, message = "Password is too long")
            String currentPassword,

            @NotBlank(message = "New password is required")
            @Size(max = 200, message = "Password is too long")
            String newPassword
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "New temporary password is required")
            @Size(max = 200, message = "Password is too long")
            String newPassword
    ) {
    }

    /** "Who am I" information. Never contains the password or token. */
    public record MeResponse(
            Long id,
            String employeeCode,
            String fullName,
            String email,
            String roleCode,
            String roleName,
            String userGroup,
            boolean mustChangePassword,
            Instant lastLoginAt
    ) {
        public static MeResponse from(AppUser u) {
            return new MeResponse(u.getId(), u.getEmployeeCode(), u.getFullName(), u.getEmail(),
                    u.getRole().getCode(), u.getRole().getName(), u.getRole().getUserGroup(),
                    u.isMustChangePassword(), u.getLastLoginAt());
        }
    }

    /** Sent once, after a successful login. The token is shown only here. */
    public record LoginResponse(
            String token,
            Instant expiresAt,
            boolean mustChangePassword,
            MeResponse user
    ) {
    }

    public record MessageResponse(String message) {
    }
}