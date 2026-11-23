package com.connectprojects.backend.reports.monthlylog;

import java.time.LocalDate;
import java.util.Map;

public record MonthlyLogData(
        int sourceRow,
        String ticketId,
        LocalDate createdDate,
        LocalDate closedDate,
        String assignee,
        String closedBy,
        String status,
        String priority,
        String source,
        String issueType,
        String plan,
        String topic,
        String category,
        String subcategory,
        String requester,
        String requesterRole,
        Boolean escalated,
        Long resolutionSeconds,
        Map<String, String> values
) {
}
