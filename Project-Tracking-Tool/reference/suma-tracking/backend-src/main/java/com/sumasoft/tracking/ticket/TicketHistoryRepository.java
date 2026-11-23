package com.sumasoft.tracking.ticket;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Saves and reads ticket history rows. (Rows are only ever added.)
 */
public interface TicketHistoryRepository extends JpaRepository<TicketHistory, Long> {

    @EntityGraph(attributePaths = "changedBy")
    List<TicketHistory> findByTicketIdOrderByIdAsc(Long ticketId);
}