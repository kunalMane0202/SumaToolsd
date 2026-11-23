package com.sumasoft.tracking.team;

import java.time.Instant;

/**
 * What the API sends back for one team membership.
 */
public record AssignmentResponse(
        Long id,
        Long projectId,
        String projectName,
        String clientName,
        Long userId,
        String employeeCode,
        String fullName,
        String userRoleCode,
        String projectRole,
        String supportLevel,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static AssignmentResponse from(ProjectAssignment a) {
        return new AssignmentResponse(
                a.getId(),
                a.getProject().getId(),
                a.getProject().getProjectName(),
                a.getProject().getClient().getClientName(),
                a.getUser().getId(),
                a.getUser().getEmployeeCode(),
                a.getUser().getFullName(),
                a.getUser().getRole().getCode(),
                a.getProjectRole(),
                a.getSupportLevel(),
                a.isActive(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}