package com.sumasoft.tracking.sla;

import com.sumasoft.tracking.settings.SettingsService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out the SLA state of tickets (Step 37). Used by the SLA page and by Automation.
 *
 * Rules:
 *  - The clock starts at the ticket's creation time (from the tool or the sheet).
 *  - First response: the recorded first response time; for a finished ticket without one,
 *    its resolution time; for a ticket that is waiting on someone (a reply was sent) without
 *    a recorded time, it is "not measured".
 *  - Resolution: creation to resolved / closed time. Open tickets are measured until now.
 *  - Open tickets: ON_TRACK, AT_RISK (warning level reached, e.g. 80% of the target) or BREACHED.
 *    Tickets waiting on the customer / a third party / on hold are PAUSED when the project says so.
 *  - Finished tickets: MET or BREACHED.
 *  - A ticket without a priority is treated as MEDIUM.
 */
@Component
public class SlaEngine {

    public static final String MET = "MET";
    public static final String BREACHED = "BREACHED";
    public static final String ON_TRACK = "ON_TRACK";
    public static final String AT_RISK = "AT_RISK";
    public static final String PAUSED = "PAUSED";
    public static final String NOT_MEASURED = "NOT_MEASURED";

    public static final Set<String> WAITING_STATUSES = Set.of("PENDING_USER", "PENDING_THIRD_PARTY", "ON_HOLD");
    public static final List<String> PRIORITIES = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");

    /** Starting targets in minutes: first response, resolution. */
    public static final Map<String, int[]> DEFAULT_TARGETS = Map.of(
            "CRITICAL", new int[] {60, 240},
            "HIGH", new int[] {120, 480},
            "MEDIUM", new int[] {240, 1440},
            "LOW", new int[] {480, 2880});

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    public static final String TICKET_SELECT = "SELECT t.id, t.project_id, t.client_id, p.project_name, "
            + "COALESCE(NULLIF(BTRIM(t.external_ticket_id), ''), 'TK-' || lpad(t.id::text, 6, '0')) AS ticket_number, "
            + "t.subject, COALESCE(t.priority_code, 'MEDIUM') AS priority, s.code AS status_code, s.name AS status_name, "
            + "s.status_group, t.created_at_source, t.first_response_at, "
            + "COALESCE(t.resolved_at, t.closed_at) AS done_at, t.escalated, t.assignee_user_id, "
            + "COALESCE(u.full_name, NULLIF(INITCAP(LOWER(BTRIM(t.assignee_name_raw))), '')) AS assignee "
            + "FROM tickets t JOIN projects p ON p.id = t.project_id "
            + "JOIN ticket_statuses s ON s.id = t.status_id "
            + "LEFT JOIN users u ON u.id = t.assignee_user_id ";

    public static final String DONE = "s.status_group IN ('RESOLVED', 'CLOSED')";

    private final JdbcTemplate jdbc;
    private final SettingsService settings;

    public SlaEngine(DataSource dataSource, SettingsService settings) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.settings = settings;
    }

    /** Targets for projects that have none of their own (Settings page, "SLA starting targets"). */
    public Map<String, int[]> defaultTargets() {
        return settings == null ? DEFAULT_TARGETS : settings.slaDefaults();
    }

    public record Policy(long projectId, ZoneId zone, String hoursMode, BusinessCalendar calendar, int warnPercent,
                         boolean pauseWhenWaiting, Map<String, int[]> targets) {

        public int[] target(String priority) {
            int[] t = targets.get(priority);
            return t != null ? t : targets.getOrDefault("MEDIUM", DEFAULT_TARGETS.get("MEDIUM"));
        }
    }

    public record TicketRow(long id, long projectId, Long clientId, String projectName, String number, String subject,
                            String priority, String statusCode, String statusName, String statusGroup,
                            Instant createdAt, Instant firstResponseAt, Instant doneAt, boolean escalated,
                            Long assigneeUserId, String assignee) {

        public boolean done() {
            return "RESOLVED".equals(statusGroup) || "CLOSED".equals(statusGroup);
        }
    }

    public record Result(String responseState, long responseTarget, long responseElapsed, Instant responseDue,
                         String resolutionState, long resolutionTarget, long resolutionElapsed, Instant resolutionDue,
                         boolean open) {
    }

    /** SLA settings of every project (projects without saved settings get the starting values). */
    public Map<Long, Policy> policies() {
        Map<String, int[]> defaults = defaultTargets();
        Map<Long, Map<String, int[]>> targets = new HashMap<>();
        jdbc.query("SELECT project_id, priority_code, first_response_minutes, resolution_minutes FROM sla_targets", rs -> {
            targets.computeIfAbsent(rs.getLong("project_id"), k -> new LinkedHashMap<>())
                    .put(rs.getString("priority_code"),
                            new int[] {rs.getInt("first_response_minutes"), rs.getInt("resolution_minutes")});
        });
        Map<Long, Set<LocalDate>> holidays = new HashMap<>();
        jdbc.query("SELECT project_id, holiday_date FROM sla_holidays", rs -> {
            holidays.computeIfAbsent(rs.getLong("project_id"), k -> new HashSet<>())
                    .add(rs.getDate("holiday_date").toLocalDate());
        });
        Map<Long, Policy> result = new HashMap<>();
        jdbc.query("SELECT p.id, p.time_zone, c.hours_mode, c.work_days, c.day_start, c.day_end, c.warn_percent, "
                + "c.pause_when_waiting FROM projects p LEFT JOIN sla_calendars c ON c.project_id = p.id", rs -> {
            long id = rs.getLong("id");
            String mode = rs.getString("hours_mode") == null ? "TWENTY_FOUR_SEVEN" : rs.getString("hours_mode");
            String days = rs.getString("work_days") == null ? "MON,TUE,WED,THU,FRI" : rs.getString("work_days");
            LocalTime start = rs.getTime("day_start") == null ? LocalTime.of(9, 0) : rs.getTime("day_start").toLocalTime();
            LocalTime end = rs.getTime("day_end") == null ? LocalTime.of(18, 0) : rs.getTime("day_end").toLocalTime();
            int warn = rs.getObject("warn_percent") == null ? 80 : rs.getInt("warn_percent");
            boolean pause = rs.getObject("pause_when_waiting") == null || rs.getBoolean("pause_when_waiting");
            Map<String, int[]> t = new HashMap<>(defaults);
            t.putAll(targets.getOrDefault(id, Map.of()));
            ZoneId zone = zone(rs.getString("time_zone"));
            BusinessCalendar cal = new BusinessCalendar(zone, "TWENTY_FOUR_SEVEN".equals(mode),
                    parseDays(days), start, end, holidays.getOrDefault(id, Set.of()));
            result.put(id, new Policy(id, zone, mode, cal, warn, pause, t));
        });
        return result;
    }

    /** Tickets for SLA work. 'where' starts with "WHERE ..." and uses the aliases t, p, s, u. */
    public List<TicketRow> tickets(String where, Object... args) {
        return jdbc.query(TICKET_SELECT + where, (rs, n) -> row(rs), args);
    }

    public Result evaluate(TicketRow t, Policy p, Instant now) {
        int[] target = p.target(t.priority());
        long responseTarget = target[0];
        long resolutionTarget = target[1];
        BusinessCalendar cal = p.calendar();
        Instant created = t.createdAt();
        if (created == null) {
            return new Result(NOT_MEASURED, responseTarget, 0, null, NOT_MEASURED, resolutionTarget, 0, null, !t.done());
        }
        Instant responseDue = cal.plusMinutes(created, responseTarget);
        Instant resolutionDue = cal.plusMinutes(created, resolutionTarget);
        boolean done = t.done();
        boolean waiting = WAITING_STATUSES.contains(t.statusCode());
        boolean doneAtValid = t.doneAt() != null && !t.doneAt().isBefore(created);

        // ---- first response
        String responseState;
        long responseElapsed = 0;
        if (t.firstResponseAt() != null) {
            if (t.firstResponseAt().isBefore(created)) {
                responseState = NOT_MEASURED;
            } else {
                responseElapsed = cal.minutesBetween(created, t.firstResponseAt());
                responseState = responseElapsed <= responseTarget ? MET : BREACHED;
            }
        } else if (done) {
            if (doneAtValid) {
                responseElapsed = cal.minutesBetween(created, t.doneAt());
                responseState = responseElapsed <= responseTarget ? MET : BREACHED;
            } else {
                responseState = NOT_MEASURED;
            }
        } else if ("PENDING".equals(t.statusGroup())) {
            responseState = NOT_MEASURED; // a reply was sent, but its time was not recorded
        } else {
            responseElapsed = cal.minutesBetween(created, now);
            responseState = running(responseElapsed, responseTarget, p.warnPercent());
        }

        // ---- resolution
        String resolutionState;
        long resolutionElapsed = 0;
        if (done) {
            if (doneAtValid) {
                resolutionElapsed = cal.minutesBetween(created, t.doneAt());
                resolutionState = resolutionElapsed <= resolutionTarget ? MET : BREACHED;
            } else {
                resolutionState = NOT_MEASURED;
            }
        } else {
            resolutionElapsed = cal.minutesBetween(created, now);
            resolutionState = waiting && p.pauseWhenWaiting()
                    ? PAUSED : running(resolutionElapsed, resolutionTarget, p.warnPercent());
        }
        return new Result(responseState, responseTarget, responseElapsed, responseDue,
                resolutionState, resolutionTarget, resolutionElapsed, resolutionDue, !done);
    }

    private static String running(long elapsed, long target, int warnPercent) {
        if (elapsed >= target) {
            return BREACHED;
        }
        return elapsed * 100 >= target * warnPercent ? AT_RISK : ON_TRACK;
    }

    private static TicketRow row(ResultSet rs) throws SQLException {
        return new TicketRow(rs.getLong("id"), rs.getLong("project_id"), (Long) rs.getObject("client_id", Long.class),
                rs.getString("project_name"), rs.getString("ticket_number"), rs.getString("subject"),
                rs.getString("priority"), rs.getString("status_code"), rs.getString("status_name"),
                rs.getString("status_group"), instant(rs, "created_at_source"), instant(rs, "first_response_at"),
                instant(rs, "done_at"), rs.getBoolean("escalated"),
                (Long) rs.getObject("assignee_user_id", Long.class), rs.getString("assignee"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    static Set<DayOfWeek> parseDays(String csv) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String d : csv.split(",")) {
            switch (d.trim()) {
                case "MON" -> days.add(DayOfWeek.MONDAY);
                case "TUE" -> days.add(DayOfWeek.TUESDAY);
                case "WED" -> days.add(DayOfWeek.WEDNESDAY);
                case "THU" -> days.add(DayOfWeek.THURSDAY);
                case "FRI" -> days.add(DayOfWeek.FRIDAY);
                case "SAT" -> days.add(DayOfWeek.SATURDAY);
                case "SUN" -> days.add(DayOfWeek.SUNDAY);
                default -> { }
            }
        }
        return days;
    }

    static ZoneId zone(String name) {
        try {
            return name == null || name.isBlank() ? INDIA : ZoneId.of(name.trim());
        } catch (DateTimeException e) {
            return INDIA;
        }
    }
}