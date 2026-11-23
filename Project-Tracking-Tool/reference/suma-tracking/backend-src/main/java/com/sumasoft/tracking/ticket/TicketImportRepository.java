package com.sumasoft.tracking.ticket;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Lookups used only by the Excel/CSV import (Step 32b):
 * "which of these external ticket numbers do we already have for this project and system?"
 * (Saving still goes through TicketRepository.)
 */
public interface TicketImportRepository extends Repository<Ticket, Long> {

    @EntityGraph(attributePaths = {"status", "priority", "assignee", "closedBy"})
    List<Ticket> findByProjectIdAndSourceSystemAndExternalTicketIdIn(Long projectId, String sourceSystem,
                                                                     Collection<String> externalTicketIds);
}