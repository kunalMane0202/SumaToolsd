package com.sumasoft.tracking.ticket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row of "ticket_priorities" (Flyway V6): CRITICAL, HIGH, MEDIUM, LOW. Read-only in the program.
 */
@Entity
@Table(name = "ticket_priorities")
public class TicketPriority {

    @Id
    @Column(name = "code", length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 50, insertable = false, updatable = false)
    private String name;

    @Column(name = "sort_order", nullable = false, insertable = false, updatable = false)
    private Integer sortOrder;

    /** Needed by JPA. Not for normal use. */
    protected TicketPriority() {
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public Integer getSortOrder() { return sortOrder; }
}