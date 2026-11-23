package com.sumasoft.tracking.client;

import java.time.Instant;
import java.time.LocalDate;

/**
 * What the API sends back for one client.
 * Kept separate from the database class, so we control exactly what is shown.
 */
public record ClientResponse(
        Long id,
        String clientCode,
        String clientName,
        String companyName,
        String contactPerson,
        String email,
        String contactNumber,
        String address,
        String businessDomain,
        String status,
        LocalDate onboardingDate,
        String remarks,
        Instant createdAt,
        Instant updatedAt
) {

    /** Turns a database row into an API answer. Client code looks like CL-0001. */
    public static ClientResponse from(Client c) {
        return new ClientResponse(
                c.getId(),
                String.format("CL-%04d", c.getId()),
                c.getClientName(),
                c.getCompanyName(),
                c.getContactPerson(),
                c.getEmail(),
                c.getContactNumber(),
                c.getAddress(),
                c.getBusinessDomain(),
                c.getStatus(),
                c.getOnboardingDate(),
                c.getRemarks(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}