package com.connectprojects.backend.reports.monthlylog;

import java.util.List;

public record MonthlyLogSummaryResponse(
        long totalTickets,
        long uniqueTickets,
        long resolvedTickets,
        long openTickets,
        long escalatedTickets,
        Double averageResolutionHours,
        long resolutionSlaEligible,
        long resolutionSlaMet,
        long resolutionSlaUnscored,
        List<MonthlyLogBreakdown> byAssignee,
        List<MonthlyLogBreakdown> byResolvedAssignee,
        List<MonthlyLogBreakdown> byClosedBy,
        List<MonthlyLogBreakdown> byStatus,
        List<MonthlyLogBreakdown> byPriority,
        List<MonthlyLogBreakdown> bySource,
        List<MonthlyLogBreakdown> byIssueType,
        List<MonthlyLogBreakdown> byPlan,
        List<MonthlyLogBreakdown> byTopic,
        List<MonthlyLogBreakdown> byCategory,
        List<MonthlyLogBreakdown> bySubcategory,
        List<MonthlyLogBreakdown> byRequesterRole,
        List<MonthlyLogBreakdown> bySatisfactionRating,
        List<MonthlyLogTrendPoint> trends,
        String responseSlaNote,
        String resolutionSlaNote,
        String taskBreakdownNote
) {
}
