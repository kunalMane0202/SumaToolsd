package com.sumasoft.tracking.admin;

import com.sumasoft.tracking.validation.InputRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The Client Management screen (Step 35): one form with
 * Name, Designation, Company Email ID, Contact Number, Project Name, Account Manager (Yes / No + who).
 */
public final class AdminClientDtos {

    private AdminClientDtos() {
    }

    public record ClientForm(
            @NotBlank(message = "Name is required")
            @Size(min = 2, max = 150, message = "Name must be 2 to 150 characters")
            @Pattern(regexp = InputRules.ORG_NAME, message = InputRules.ORG_NAME_MESSAGE)
            String name,

            @Size(max = 100, message = "Designation must be at most 100 characters")
            @Pattern(regexp = "^$|" + InputRules.TITLE, message = "Designation: " + InputRules.TITLE_MESSAGE)
            String designation,

            @NotBlank(message = "Company Email ID is required")
            @Size(max = 254, message = "Company email must be at most 254 characters")
            @Pattern(regexp = InputRules.EMAIL, message = InputRules.EMAIL_MESSAGE)
            String email,

            @NotBlank(message = "Contact Number is required")
            @Pattern(regexp = InputRules.PHONE, message = InputRules.PHONE_MESSAGE)
            String contactNumber,

            @Size(max = 150, message = "Project name must be at most 150 characters")
            @Pattern(regexp = "^$|" + InputRules.ORG_NAME, message = "Project name: " + InputRules.ORG_NAME_MESSAGE)
            String projectName,

            Boolean accountManagerAssigned,

            Long accountManagerUserId
    ) {
    }

    public record ClientRow(
            Long id,
            String clientCode,
            String name,
            String designation,
            String email,
            String contactNumber,
            String status,
            boolean accountManagerAssigned,
            Long accountManagerUserId,
            String accountManagerName,
            List<String> projects
    ) {
    }
}