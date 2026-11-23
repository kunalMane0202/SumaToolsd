package com.sumasoft.tracking.ticket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row of "ticket_statuses" (Flyway V6 + V7). Read-only in the program.
 * statusGroup is used for dashboard cards: OPEN, IN_PROGRESS, PENDING, RESOLVED, CLOSED.
 */
@Entity
@Table(name = "ticket_statuses")
public class TicketStatus {

    @Id
    private Long id;

    @Column(name = "code", nullable = false, length = 40, insertable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 100, insertable = false, updatable = false)
    private String name;

    @Column(name = "status_group", nullable = false, length = 20, insertable = false, updatable = false)
    private String statusGroup;

    @Column(name = "is_final", nullable = false, insertable = false, updatable = false)
    private boolean finalStatus;

    @Column(name = "sort_order", nullable = false, insertable = false, updatable = false)
    private Integer sortOrder;

    @Column(name = "active", nullable = false, insertable = false, updatable = false)
    private boolean active;

    /** Needed by JPA. Not for normal use. */
    protected TicketStatus() {
    }

    /** Resolved, Closed and Merged tickets are "done" (no more ageing). */
    public boolean isDone() {
        return finalStatus || "RESOLVED".equals(statusGroup) || "CLOSED".equals(statusGroup);
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getStatusGroup() { return statusGroup; }
    public boolean isFinalStatus() { return finalStatus; }
    public Integer getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
}