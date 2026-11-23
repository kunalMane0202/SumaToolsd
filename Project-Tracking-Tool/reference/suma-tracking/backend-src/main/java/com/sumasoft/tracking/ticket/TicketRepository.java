package com.sumasoft.tracking.ticket;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * Reads and saves tickets.
 * "Specification" = a search built from the filters the user chose (see TicketService).
 */
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    /** Search with filters, one page at a time, loading client/project/status/etc. in one go. */
    @Override
    @EntityGraph(attributePaths = {"client", "project", "status", "priority", "assignee", "closedBy"})
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);

    /** One ticket with everything needed to show it. */
    @EntityGraph(attributePaths = {"client", "project", "status", "priority", "assignee", "closedBy"})
    Optional<Ticket> findWithDetailsById(Long id);
}