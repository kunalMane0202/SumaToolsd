package com.sumasoft.tracking.user;

import java.time.Instant;
import java.time.LocalTime;

/**
 * What the API sends back for one employee.
 */
public record UserResponse(
        Long id,
        String employeeCode,
        String fullName,
        String email,
        String contactNumber,
        String designation,
        String department,
        String skillSet,
        String roleCode,
        String roleName,
        String userGroup,
        Long reportingManagerId,
        String reportingManagerName,
        LocalTime shiftStart,
        LocalTime shiftEnd,
        String weeklyOff,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserResponse from(AppUser u) {
        AppUser manager = u.getReportingManager();
        return new UserResponse(
                u.getId(),
                u.getEmployeeCode(),
                u.getFullName(),
                u.getEmail(),
                u.getContactNumber(),
                u.getDesignation(),
                u.getDepartment(),
                u.getSkillSet(),
                u.getRole().getCode(),
                u.getRole().getName(),
                u.getRole().getUserGroup(),
                manager == null ? null : manager.getId(),
                manager == null ? null : manager.getFullName(),
                u.getShiftStart(),
                u.getShiftEnd(),
                u.getWeeklyOff(),
                u.getStatus(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }
}