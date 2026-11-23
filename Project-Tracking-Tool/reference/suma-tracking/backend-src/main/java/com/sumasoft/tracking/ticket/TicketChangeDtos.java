package com.sumasoft.tracking.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The "message shapes" for working on a ticket: edit, change status, assign.
 */
public final class TicketChangeDtos {

    private TicketChangeDtos() {
    }

    /** What a person sends to edit a ticket (the whole set of editable fields). */
    public record TicketUpdateRequest(
            @NotBlank(message = "Subject is required")
            @Size(max = 500, message = "Subject must be at most 500 characters")
            String subject,

            @Size(max = 20000, message = "Description must be at most 20000 characters")
            String description,

            @Size(max = 100, message = "Category must be at most 100 characters")
            String category,

            @Size(max = 100, message = "Subcategory must be at most 100 characters")
            String subcategory,

            @Size(max = 100, message = "Issue type must be at most 100 characters")
            String issueType,

            @Size(max = 200, message = "Topic must be at most 200 characters")
            String topic,

            @Size(max = 100, message = "Plan must be at most 100 characters")
            String plan,

            @Pattern(regexp = "^[A-Za-z_]{1,20}$", message = "Priority must be CRITICAL, HIGH, MEDIUM or LOW")
            String priorityCode,

            @Size(max = 50, message = "Channel must be at most 50 characters")
            String channel,

            @Size(max = 200, message = "Requester name must be at most 200 characters")
            String requesterName,

            @Size(max = 100, message = "Requester role must be at most 100 characters")
            String requesterRole,

            Boolean escalated
    ) {
    }

    /** Change the status, e.g. {"statusCode":"IN_PROGRESS","note":"Working on it"}. */
    public record StatusChangeRequest(
            @NotBlank(message = "Status is required. See GET /api/ticket-statuses")
            @Size(max = 40, message = "Status code is too long")
            String statusCode,

            @Size(max = 500, message = "Note must be at most 500 characters")
            String note
    ) {
    }

    /** Assign or reassign. assigneeUserId empty = nobody. */
    public record AssignRequest(
            Long assigneeUserId,

            @Size(max = 500, message = "Note must be at most 500 characters")
            String note
    ) {
    }
}