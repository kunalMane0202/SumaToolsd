package com.sumasoft.tracking.project;

import java.time.Instant;
import java.time.LocalDate;

/**
 * What the API sends back for one project.
 */
public record ProjectResponse(
        Long id,
        String projectCode,
        Long clientId,
        String clientName,
        String projectName,
        String description,
        boolean supportsL1,
        boolean supportsL2,
        boolean supportsL3,
        LocalDate supportStartDate,
        LocalDate supportEndDate,
        String timeZone,
        String externalTicketSystem,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    /** Turns a database row into an API answer. Project code looks like PR-0001. */
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(
                p.getId(),
                String.format("PR-%04d", p.getId()),
                p.getClient().getId(),
                p.getClient().getClientName(),
                p.getProjectName(),
                p.getDescription(),
                p.isSupportsL1(),
                p.isSupportsL2(),
                p.isSupportsL3(),
                p.getSupportStartDate(),
                p.getSupportEndDate(),
                p.getTimeZone(),
                p.getExternalTicketSystem(),
                p.getStatus(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}