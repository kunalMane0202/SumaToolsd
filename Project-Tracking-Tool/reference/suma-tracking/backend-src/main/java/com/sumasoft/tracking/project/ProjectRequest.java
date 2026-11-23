package com.sumasoft.tracking.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * What the user sends to CREATE or EDIT a project.
 * Every field is checked before anything is saved.
 * (Status is not here: use the activate / deactivate / close addresses.)
 */
public record ProjectRequest(

        @NotNull(message = "Client is required")
        Long clientId,

        @NotBlank(message = "Project name is required")
        @Size(max = 150, message = "Project name must be at most 150 characters")
        String projectName,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        Boolean supportsL1,
        Boolean supportsL2,
        Boolean supportsL3,

        LocalDate supportStartDate,
        LocalDate supportEndDate,

        @Size(max = 64, message = "Time zone must be at most 64 characters")
        String timeZone,

        @Pattern(regexp = "^[A-Za-z0-9_]{1,50}$",
                 message = "Ticket system may contain only letters, digits and _ (for example ZENDESK)")
        String externalTicketSystem
) {
}