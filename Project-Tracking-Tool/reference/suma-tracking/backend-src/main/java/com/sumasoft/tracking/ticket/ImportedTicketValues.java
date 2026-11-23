package com.sumasoft.tracking.ticket;

import java.time.Instant;

/**
 * The checked values of ONE row of an imported Excel/CSV file, ready to be saved as a ticket.
 * (Produced by the import row checker; status and priority are already translated to our codes.)
 */
public record ImportedTicketValues(
        String externalTicketId,
        Integer importRowNumber,
        Instant createdAtSource,
        Instant resolvedAt,
        Instant closedAt,
        String statusCode,
        String priorityCode,
        String assigneeName,
        String closedByName,
        String requesterName,
        String requesterRole,
        String channel,
        boolean escalated,
        String satisfactionRating,
        String issueType,
        String plan,
        String topic,
        String category,
        String subcategory,
        String description,
        String timeframeRaw,
        String dayRaw,
        String resolutionTimeRaw
) {
}