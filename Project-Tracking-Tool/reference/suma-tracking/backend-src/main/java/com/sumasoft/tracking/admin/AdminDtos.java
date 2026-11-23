package com.sumasoft.tracking.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Requests and answers for the admin screens (Step 35):
 * new roles, client account managers, project status words, employee workload.
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** A new role made by the Super Admin, e.g. "Project Lead". Its access follows its user group. */
    public record RoleCreateRequest(
            @NotBlank(message = "Role name is required")
            @Size(max = 100, message = "Role name must be at most 100 characters")
            String name,

            @NotBlank(message = "Choose what the role may do")
            @Pattern(regexp = "^(PROJECT_MANAGER|SUPPORT_USER)$",
                    message = "Access must be PROJECT_MANAGER (leads and managers) or SUPPORT_USER (support agents)")
            String userGroup,

            @Size(max = 500, message = "Description must be at most 500 characters")
            String description
    ) {
    }

    /** userId = null removes the account manager. */
    public record AccountManagerRequest(Long userId) {
    }

    public record AccountManagerResponse(Long clientId, Long userId, String fullName) {
    }

    /** One word of a project's sheet / tool and what it means in our system. */
    public record ValueMappingRequest(
            @NotBlank(message = "Field is required")
            @Pattern(regexp = "^(STATUS|PRIORITY)$", message = "Field must be STATUS or PRIORITY")
            String field,

            @NotBlank(message = "The word in the sheet is required")
            @Size(max = 100, message = "The word must be at most 100 characters")
            String sourceValue,

            @NotBlank(message = "Choose what the word means")
            @Size(max = 40, message = "Code too long")
            String targetCode
    ) {
    }

    public record ValueMappingResponse(Long id, Long projectId, String field, String sourceValue, String targetCode,
                                       String targetName) {
    }

    /** How busy each employee is (tickets assigned to them as a linked employee). */
    public record WorkloadResponse(Long userId, long openNow, long escalatedOpen, long last30Days, long doneLast30Days) {
    }
}