package com.sumasoft.tracking.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * What the user sends to add someone to a project team (or change their job on it).
 */
public record AssignmentRequest(

        @NotNull(message = "Employee (userId) is required")
        Long userId,

        @NotBlank(message = "Project role is required: PROJECT_MANAGER, TEAM_LEADER or MEMBER")
        @Pattern(regexp = "(?i)^\\s*(PROJECT_MANAGER|TEAM_LEADER|MEMBER)\\s*$",
                 message = "Project role must be PROJECT_MANAGER, TEAM_LEADER or MEMBER")
        String projectRole,

        @Pattern(regexp = "(?i)^\\s*(L1|L2|L3)\\s*$", message = "Support level must be L1, L2 or L3")
        String supportLevel
) {
}