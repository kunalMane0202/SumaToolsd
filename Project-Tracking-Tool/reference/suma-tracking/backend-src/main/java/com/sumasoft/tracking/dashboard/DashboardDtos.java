package com.sumasoft.tracking.dashboard;

import java.time.LocalDate;
import java.util.List;

/**
 * What the dashboard web address sends back (Step 34).
 * "done" = Resolved, Closed or Merged.
 */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record DashboardResponse(
            LocalDate from,
            LocalDate to,
            Long projectId,
            Summary summary,
            List<DayPoint> byDay,
            List<StatusCount> byStatus,
            List<NameCount> byProject,
            List<AgentStat> byAgent,
            List<NameCount> byChannel,
            List<HourCount> byHour,
            List<NameCount> byTopic
    ) {
    }

    /** The KPI cards. Times are in minutes (null when there is nothing to measure). */
    public record Summary(
            long total,
            long open,
            long inProgress,
            long pending,
            long resolved,
            long closed,
            long done,
            long escalated,
            Double avgResolutionMinutes,
            Double medianResolutionMinutes,
            long resolvedWithinOneHour
    ) {
    }

    public record DayPoint(LocalDate date, long created, long done) {
    }

    public record StatusCount(String code, String name, String group, long count) {
    }

    public record NameCount(String name, long count) {
    }

    public record AgentStat(String name, long total, long done, long escalated, Double avgResolutionMinutes) {
    }

    public record HourCount(int hour, long count) {
    }
}