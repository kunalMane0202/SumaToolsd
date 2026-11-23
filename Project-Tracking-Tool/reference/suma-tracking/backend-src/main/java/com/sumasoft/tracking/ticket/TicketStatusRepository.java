package com.sumasoft.tracking.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Reads ticket statuses.
 */
public interface TicketStatusRepository extends JpaRepository<TicketStatus, Long> {

    Optional<TicketStatus> findByCode(String code);

    List<TicketStatus> findByActiveTrueOrderBySortOrderAsc();
}