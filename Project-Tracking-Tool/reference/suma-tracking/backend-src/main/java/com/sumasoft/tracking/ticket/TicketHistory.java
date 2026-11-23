package com.sumasoft.tracking.ticket;

import com.sumasoft.tracking.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row of "ticket_history" (Flyway V6): one event in a ticket's life
 * (CREATED, STATUS_CHANGED, ASSIGNED, UPDATED, IMPORTED, SYNCED). Add-only.
 */
@Entity
@Table(name = "ticket_history")
public class TicketHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private Ticket ticket;

    @Column(name = "occurred_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant occurredAt;

    @Column(name = "event_type", nullable = false, length = 30, updatable = false)
    private String eventType;

    @Column(name = "field_name", length = 60, updatable = false)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "text", updatable = false)
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text", updatable = false)
    private String newValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id", updatable = false)
    private AppUser changedBy;

    @Column(name = "source", nullable = false, length = 20, updatable = false)
    private String source;

    @Column(name = "note", length = 500, updatable = false)
    private String note;

    /** Needed by JPA. Not for normal use. */
    protected TicketHistory() {
    }

    public static TicketHistory of(Ticket ticket, String eventType, String fieldName, String oldValue,
                                   String newValue, AppUser changedBy, String source, String note, Instant now) {
        TicketHistory h = new TicketHistory();
        h.ticket = ticket;
        h.eventType = eventType;
        h.fieldName = fieldName;
        h.oldValue = oldValue;
        h.newValue = newValue;
        h.changedBy = changedBy;
        h.source = source;
        h.note = note == null ? null : (note.length() <= 500 ? note : note.substring(0, 500));
        h.occurredAt = now;
        return h;
    }

    public Long getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getEventType() { return eventType; }
    public String getFieldName() { return fieldName; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public AppUser getChangedBy() { return changedBy; }
    public String getSource() { return source; }
    public String getNote() { return note; }
}