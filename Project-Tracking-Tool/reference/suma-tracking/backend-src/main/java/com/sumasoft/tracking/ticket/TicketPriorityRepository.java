package com.sumasoft.tracking.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Reads ticket priorities.
 */
public interface TicketPriorityRepository extends JpaRepository<TicketPriority, String> {

    List<TicketPriority> findAllByOrderBySortOrderAsc();
}