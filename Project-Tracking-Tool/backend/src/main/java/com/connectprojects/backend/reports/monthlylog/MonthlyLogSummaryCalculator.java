package com.connectprojects.backend.reports.monthlylog;

import com.connectprojects.backend.work.ProjectSlaEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

public class MonthlyLogSummaryCalculator {
    public MonthlyLogSummaryResponse calculate(List<MonthlyLogData> rows,
                                              Map<String, ProjectSlaEntity> calendarHourSlasByPriority) {
        Set<String> ticketIds = new HashSet<>();
        Map<String, Long> assignees = new HashMap<>();
        Map<String, Long> resolvedAssignees = new HashMap<>();
        Map<String, Long> closedBy = new HashMap<>();
        Map<String, Long> statuses = new HashMap<>();
        Map<String, Long> priorities = new HashMap<>();
        Map<String, Long> sources = new HashMap<>();
        Map<String, Long> issueTypes = new HashMap<>();
        Map<String, Long> plans = new HashMap<>();
        Map<String, Long> topics = new HashMap<>();
        Map<String, Long> categories = new HashMap<>();
        Map<String, Long> subcategories = new HashMap<>();
        Map<String, Long> requesterRoles = new HashMap<>();
        Map<String, Long> satisfactionRatings = new HashMap<>();
        TreeMap<LocalDate, long[]> trends = new TreeMap<>();

        long resolved = 0;
        long escalated = 0;
        long durationCount = 0;
        long totalResolutionSeconds = 0;
        long slaEligible = 0;
        long slaMet = 0;
        long slaUnscored = 0;

        for (MonthlyLogData row : rows) {
            String ticketId = normalize(row.ticketId());
            if (!ticketId.isBlank()) ticketIds.add(ticketId);
            increment(assignees, label(row.assignee(), "Unassigned"));
            increment(closedBy, label(row.closedBy(), "Not recorded"));
            increment(statuses, label(row.status(), "Unknown"));
            increment(priorities, label(row.priority(), "Unknown"));
            increment(sources, label(row.source(), "Unknown"));
            increment(issueTypes, label(row.issueType(), "Unknown"));
            increment(plans, label(row.plan(), "Not recorded"));
            increment(topics, label(row.topic(), "Not recorded"));
            increment(categories, label(row.category(), "Not recorded"));
            increment(subcategories, label(row.subcategory(), "Not recorded"));
            increment(requesterRoles, label(row.requesterRole(), "Not recorded"));
            increment(satisfactionRatings, label(satisfactionRating(row), "Not recorded"));

            boolean isResolved = isResolved(row);
            if (isResolved) {
                resolved++;
                increment(resolvedAssignees, label(row.assignee(), "Unassigned"));
            }
            if (Boolean.TRUE.equals(row.escalated())) escalated++;
            if (row.createdDate() != null) trends.computeIfAbsent(row.createdDate(), ignored -> new long[2])[0]++;
            if (row.closedDate() != null) trends.computeIfAbsent(row.closedDate(), ignored -> new long[2])[1]++;

            if (isResolved && row.resolutionSeconds() != null && row.resolutionSeconds() >= 0) {
                totalResolutionSeconds += row.resolutionSeconds();
                durationCount++;
            }

            if (isResolved) {
                ProjectSlaEntity sla = calendarHourSlasByPriority.get(normalize(row.priority()));
                if (row.resolutionSeconds() == null || sla == null || sla.isBusinessHoursOnly()) {
                    slaUnscored++;
                } else {
                    slaEligible++;
                    if (row.resolutionSeconds() <= sla.getResolutionMinutes() * 60L) slaMet++;
                }
            }
        }

        List<MonthlyLogTrendPoint> trendPoints = new ArrayList<>();
        trends.forEach((date, counts) -> trendPoints.add(new MonthlyLogTrendPoint(date, counts[0], counts[1])));
        Double averageHours = durationCount == 0
                ? null
                : Math.round((totalResolutionSeconds / (double) durationCount / 3_600) * 100.0) / 100.0;

        return new MonthlyLogSummaryResponse(
                rows.size(),
                ticketIds.size(),
                resolved,
                rows.size() - resolved,
                escalated,
                averageHours,
                slaEligible,
                slaMet,
                slaUnscored,
                breakdown(assignees),
                breakdown(resolvedAssignees),
                breakdown(closedBy),
                breakdown(statuses),
                breakdown(priorities),
                breakdown(sources),
                breakdown(issueTypes),
                breakdown(plans),
                breakdown(topics),
                breakdown(categories),
                breakdown(subcategories),
                breakdown(requesterRoles),
                breakdown(satisfactionRatings),
                List.copyOf(trendPoints),
                "The workbook has no first-response time, so response SLA cannot be calculated.",
                slaNote(slaEligible, slaUnscored),
                "The workbook has no task ID or hours-worked field. Topic and category are shown as the available work breakdowns."
        );
    }

    private static boolean isResolved(MonthlyLogData row) {
        String status = normalize(row.status());
        return row.closedDate() != null || Set.of("solved", "resolved", "closed", "merged").contains(status);
    }

    private static String slaNote(long eligible, long unscored) {
        if (eligible == 0) {
            return "No compatible calendar-hours resolution SLA was available; business-hours targets and rows without a duration are not scored.";
        }
        return unscored == 0
                ? "Compared with configured calendar-hours resolution targets."
                : "Compared with configured calendar-hours resolution targets; business-hours targets and incomplete rows are not scored.";
    }

    private static List<MonthlyLogBreakdown> breakdown(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> new MonthlyLogBreakdown(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(MonthlyLogBreakdown::count).reversed()
                        .thenComparing(MonthlyLogBreakdown::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static void increment(Map<String, Long> counts, String label) {
        counts.merge(label, 1L, Long::sum);
    }

    private static String satisfactionRating(MonthlyLogData row) {
        if (row.values() == null) return "";
        return row.values().entrySet().stream()
                .filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "")
                        .equals("satisfactionrating"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("");
    }

    private static String label(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
