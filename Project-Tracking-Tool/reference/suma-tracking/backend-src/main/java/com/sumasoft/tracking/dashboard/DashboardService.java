package com.sumasoft.tracking.dashboard;

import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import com.sumasoft.tracking.dashboard.DashboardDtos.AgentStat;
import com.sumasoft.tracking.dashboard.DashboardDtos.DashboardResponse;
import com.sumasoft.tracking.dashboard.DashboardDtos.DayPoint;
import com.sumasoft.tracking.dashboard.DashboardDtos.HourCount;
import com.sumasoft.tracking.dashboard.DashboardDtos.NameCount;
import com.sumasoft.tracking.dashboard.DashboardDtos.StatusCount;
import com.sumasoft.tracking.dashboard.DashboardDtos.Summary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Counts for the dashboard (FRD: KPI cards, trends, agent performance), straight from the database.
 *  - Only tickets of projects the person may see (Super Admin / Management Viewer: all projects).
 *  - from / to = ticket creation date in the project's own time zone (both days included).
 *  - "done" = status group RESOLVED or CLOSED (Resolved, Closed, Merged).
 *  - Resolution time = creation to closure (or resolution); impossible negative times are ignored.
 *  - Agent names: the linked employee's name, otherwise the name typed in the sheet
 *    (capital letters and spaces cleaned, so "sujit" and "Sujit " count as one person).
 */
@Service
public class DashboardService {

    private static final int MAX_RANGE_DAYS = 400;

    private final AccessService access;
    private final JdbcTemplate jdbc;

    public DashboardService(AccessService access, DataSource dataSource) {
        this.access = access;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(Long projectId, LocalDate from, LocalDate to) {
        AuthenticatedUser me = access.current();
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        }
        if (from != null && to != null && from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose at most " + MAX_RANGE_DAYS + " days");
        }

        // ---------- which tickets: WHERE part + its values
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (projectId != null) {
            if (!access.canSeeProject(me, projectId)) {
                throw AccessService.notFound("Project");
            }
            where.append(" AND t.project_id = ?");
            args.add(projectId);
        } else if (!access.seesEverything(me)) {
            Set<Long> visible = access.visibleProjectIds(me);
            if (visible.isEmpty()) {
                where.append(" AND 1 = 0");
            } else {
                where.append(" AND t.project_id IN (").append("?,".repeat(visible.size() - 1)).append("?)");
                args.addAll(visible);
            }
        }
        if (from != null) {
            where.append(" AND (t.created_at_source AT TIME ZONE p.time_zone)::date >= ?");
            args.add(Date.valueOf(from));
        }
        if (to != null) {
            where.append(" AND (t.created_at_source AT TIME ZONE p.time_zone)::date <= ?");
            args.add(Date.valueOf(to));
        }
        Object[] a = args.toArray();

        String base = " FROM tickets t JOIN projects p ON p.id = t.project_id "
                + "JOIN ticket_statuses s ON s.id = t.status_id "
                + "LEFT JOIN users u ON u.id = t.assignee_user_id" + where;
        String done = "s.status_group IN ('RESOLVED', 'CLOSED')";
        // ::float8 so the database returns a normal decimal number (Java Double), not a "numeric"
        String minutes = "(EXTRACT(EPOCH FROM (COALESCE(t.closed_at, t.resolved_at) - t.created_at_source)) / 60.0)::float8";
        String measurable = "COALESCE(t.closed_at, t.resolved_at) >= t.created_at_source";
        String agent = "COALESCE(u.full_name, NULLIF(INITCAP(LOWER(BTRIM(t.assignee_name_raw))), ''), 'Unassigned')";

        // ---------- KPI cards
        Summary summary = jdbc.queryForObject("SELECT COUNT(*) AS total, "
                + "COUNT(*) FILTER (WHERE s.status_group = 'OPEN') AS open, "
                + "COUNT(*) FILTER (WHERE s.status_group = 'IN_PROGRESS') AS in_progress, "
                + "COUNT(*) FILTER (WHERE s.status_group = 'PENDING') AS pending, "
                + "COUNT(*) FILTER (WHERE s.status_group = 'RESOLVED') AS resolved, "
                + "COUNT(*) FILTER (WHERE s.status_group = 'CLOSED') AS closed, "
                + "COUNT(*) FILTER (WHERE " + done + ") AS done, "
                + "COUNT(*) FILTER (WHERE t.escalated) AS escalated, "
                + "AVG(" + minutes + ") FILTER (WHERE " + measurable + ") AS avg_min, "
                + "PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY " + minutes + ") FILTER (WHERE " + measurable + ") AS median_min, "
                + "COUNT(*) FILTER (WHERE " + measurable + " AND " + minutes + " <= 60) AS within_hour"
                + base,
                (rs, n) -> new Summary(rs.getLong("total"), rs.getLong("open"), rs.getLong("in_progress"),
                        rs.getLong("pending"), rs.getLong("resolved"), rs.getLong("closed"), rs.getLong("done"),
                        rs.getLong("escalated"), round(doubleOrNull(rs, "avg_min")),
                        round(doubleOrNull(rs, "median_min")), rs.getLong("within_hour")),
                a);

        // ---------- trend per day
        List<DayPoint> byDay = jdbc.query("SELECT (t.created_at_source AT TIME ZONE p.time_zone)::date AS d, "
                        + "COUNT(*) AS created, COUNT(*) FILTER (WHERE " + done + ") AS done"
                        + base + " GROUP BY d ORDER BY d",
                (rs, n) -> new DayPoint(rs.getDate("d").toLocalDate(), rs.getLong("created"), rs.getLong("done")), a);

        // ---------- per status
        List<StatusCount> byStatus = jdbc.query("SELECT s.code, s.name, s.status_group, s.sort_order, COUNT(*) AS c"
                        + base + " GROUP BY s.code, s.name, s.status_group, s.sort_order ORDER BY s.sort_order",
                (rs, n) -> new StatusCount(rs.getString("code"), rs.getString("name"),
                        rs.getString("status_group"), rs.getLong("c")), a);

        // ---------- per project
        List<NameCount> byProject = jdbc.query("SELECT p.project_name AS name, COUNT(*) AS c"
                        + base + " GROUP BY p.project_name ORDER BY c DESC",
                (rs, n) -> new NameCount(rs.getString("name"), rs.getLong("c")), a);

        // ---------- per agent (top 20)
        List<AgentStat> byAgent = jdbc.query("SELECT " + agent + " AS name, COUNT(*) AS total, "
                        + "COUNT(*) FILTER (WHERE " + done + ") AS done, "
                        + "COUNT(*) FILTER (WHERE t.escalated) AS escalated, "
                        + "AVG(" + minutes + ") FILTER (WHERE " + measurable + ") AS avg_min"
                        + base + " GROUP BY 1 ORDER BY total DESC LIMIT 20",
                (rs, n) -> new AgentStat(rs.getString("name"), rs.getLong("total"), rs.getLong("done"),
                        rs.getLong("escalated"), round(doubleOrNull(rs, "avg_min"))), a);

        // ---------- per channel (Email / Chat / Call ...)
        List<NameCount> byChannel = jdbc.query("SELECT COALESCE(NULLIF(INITCAP(LOWER(BTRIM(t.channel))), ''), 'Unknown') AS name, "
                        + "COUNT(*) AS c" + base + " GROUP BY 1 ORDER BY c DESC",
                (rs, n) -> new NameCount(rs.getString("name"), rs.getLong("c")), a);

        // ---------- per hour of the day (project time)
        List<HourCount> byHour = jdbc.query("SELECT EXTRACT(HOUR FROM t.created_at_source AT TIME ZONE p.time_zone)::int AS h, "
                        + "COUNT(*) AS c" + base + " GROUP BY h ORDER BY h",
                (rs, n) -> new HourCount(rs.getInt("h"), rs.getLong("c")), a);

        // ---------- top topics
        List<NameCount> byTopic = jdbc.query("SELECT COALESCE(NULLIF(BTRIM(t.topic), ''), 'Not set') AS name, COUNT(*) AS c"
                        + base + " GROUP BY 1 ORDER BY c DESC LIMIT 10",
                (rs, n) -> new NameCount(rs.getString("name"), rs.getLong("c")), a);

        return new DashboardResponse(from, to, projectId, summary, byDay, byStatus, byProject, byAgent,
                byChannel, byHour, byTopic);
    }

    private static Double doubleOrNull(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : v;
    }

    private static Double round(Double v) {
        return v == null ? null : Math.round(v * 10.0) / 10.0;
    }
}