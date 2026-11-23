package com.sumasoft.tracking.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * What the user sends to CREATE or EDIT an employee.
 * Every field is checked before anything is saved.
 * (Status is not here: use the activate / deactivate addresses.)
 */
public record UserRequest(

        @NotBlank(message = "Employee ID is required")
        @Pattern(regexp = "^[A-Za-z0-9_\\-]{1,30}$",
                 message = "Employee ID may contain only letters, digits, _ and - (max 30)")
        String employeeCode,

        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must be at most 150 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is not valid")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        @Pattern(regexp = "^[0-9+()\\- ]{6,30}$",
                 message = "Contact number may contain only digits, spaces, + - ( ) and must be 6 to 30 characters")
        String contactNumber,

        @Size(max = 100, message = "Designation must be at most 100 characters")
        String designation,

        @Size(max = 100, message = "Department must be at most 100 characters")
        String department,

        @Size(max = 2000, message = "Skill set must be at most 2000 characters")
        String skillSet,

        @NotBlank(message = "Role is required (for example L1_SUPPORT)")
        String roleCode,

        Long reportingManagerId,

        LocalTime shiftStart,
        LocalTime shiftEnd,

        @Pattern(regexp = "(?i)^\\s*(MON|TUE|WED|THU|FRI|SAT|SUN)(\\s*,\\s*(MON|TUE|WED|THU|FRI|SAT|SUN)){0,6}\\s*$",
                 message = "Weekly off must look like SAT,SUN")
        String weeklyOff
) {
}