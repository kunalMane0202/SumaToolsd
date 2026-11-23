package com.sumasoft.tracking.sla;

import com.sumasoft.tracking.audit.AuditService;
import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import com.sumasoft.tracking.rbac.RbacService;
import com.sumasoft.tracking.sla.SlaDtos.AttentionTicket;
import com.sumasoft.tracking.sla.SlaDtos.Holiday;
import com.sumasoft.tracking.sla.SlaDtos.HolidayForm;
import com.sumasoft.tracking.sla.SlaDtos.Overview;
import com.sumasoft.tracking.sla.SlaDtos.ProjectSla;
import com.sumasoft.tracking.sla.SlaDtos.Settings;
import com.sumasoft.tracking.sla.SlaDtos.SettingsForm;
import com.sumasoft.tracking.sla.SlaDtos.Target;
import com.sumasoft.tracking.sla.SlaDtos.TargetForm;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SLA Management page (Step 37).
 *  - Settings per project: support hours, warning level, pause rule, targets per priority, holidays.
 *    Roles with SLA_VIEW see them for their own projects; SLA_MANAGE changes them (audited).
 *  - Overview: SLA % of tickets created in the chosen days, and the open tickets that need attention.
 */
@Service
public class SlaService {

    private static final int MAX_RANGE_DAYS = 400;
    private static final int OPEN_LOOKBACK_DAYS = 90;
    private static final int MAX_ATTENTION = 300;

    private final AccessService access;
    private final AuditService audit;
    private final SlaEngine engine;
    private final RbacService rbac;
    private final JdbcTemplate jdbc;

    public SlaService(AccessService access, AuditService audit, SlaEngine engine, RbacService rbac, DataSource dataSource) {
        this.access = access;
        this.audit = audit;
        this.engine = engine;
        this.rbac = rbac;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ================================================================ settings

    @Transactional(readOnly = true)
    public Settings settings(Long projectId) {
        AuthenticatedUser me = access.current();
        requireLeadView(me, projectId);
        return load(projectId);
    }

    @Transactional
    public Settings save(Long projectId, SettingsForm f) {
        AuthenticatedUser me = access.current();
        rbac.require(me, "SLA_MANAGE");
        if (!access.canSeeProject(me, projectId)) {
            throw AccessService.notFound("Project");
        }
        Settings before = load(projectId);

        LocalTime start = LocalTime.parse(f.dayStart());
        LocalTime end = LocalTime.parse(f.dayEnd());
        if (!end.isAfter(start)) {
            throw bad("Day end must be after day start (for example 09:00 to 18:00)");
        }
        Set<String> seen = new HashSet<>();
        for (TargetForm t : f.targets()) {
            if (!seen.add(t.priorityCode())) {
                throw bad("Each priority can have only one target");
            }
            if (t.resolutionMinutes() < t.firstResponseMinutes()) {
                throw bad("For " + label(t.priorityCode()) + ", the resolution target cannot be shorter than the first response target");
            }
        }
        List<HolidayForm> holidays = f.holidays() == null ? List.of() : f.holidays();
        Set<LocalDate> days = new HashSet<>();
        for (HolidayForm h : holidays) {
            if (!days.add(h.date())) {
                throw bad("The holiday " + h.date() + " is listed twice");
            }
        }

        jdbc.update("INSERT INTO sla_calendars (project_id, hours_mode, work_days, day_start, day_end, warn_percent, "
                        + "pause_when_waiting, updated_at, updated_by_user_id) VALUES (?, ?, ?, ?, ?, ?, ?, now(), ?) "
                        + "ON CONFLICT (project_id) DO UPDATE SET hours_mode = EXCLUDED.hours_mode, "
                        + "work_days = EXCLUDED.work_days, day_start = EXCLUDED.day_start, day_end = EXCLUDED.day_end, "
                        + "warn_percent = EXCLUDED.warn_percent, pause_when_waiting = EXCLUDED.pause_when_waiting, "
                        + "updated_at = now(), updated_by_user_id = EXCLUDED.updated_by_user_id",
                projectId, f.hoursMode(), sortDays(f.workDays()), Time.valueOf(start), Time.valueOf(end),
                f.warnPercent(), f.pauseWhenWaiting(), me.userId());
        for (TargetForm t : f.targets()) {
            jdbc.update("INSERT INTO sla_targets (project_id, priority_code, first_response_minutes, resolution_minutes) "
                            + "VALUES (?, ?, ?, ?) ON CONFLICT (project_id, priority_code) DO UPDATE SET "
                            + "first_response_minutes = EXCLUDED.first_response_minutes, "
                            + "resolution_minutes = EXCLUDED.resolution_minutes",
                    projectId, t.priorityCode(), t.firstResponseMinutes(), t.resolutionMinutes());
        }
        jdbc.update("DELETE FROM sla_holidays WHERE project_id = ?", projectId);
        for (HolidayForm h : holidays) {
            jdbc.update("INSERT INTO sla_holidays (project_id, holiday_date, name) VALUES (?, ?, ?)",
                    projectId, Date.valueOf(h.date()), h.name().trim());
        }
        Settings after = load(projectId);
        Long clientId = jdbc.queryForObject("SELECT client_id FROM projects WHERE id = ?", Long.class, projectId);
        audit.record("SLA_SETTINGS_UPDATED", "PROJECT", projectId, clientId, projectId, before, after);
        return after;
    }

    private Settings load(Long projectId) {
        List<Map<String, Object>> project = jdbc.queryForList(
                "SELECT p.project_name, p.time_zone, c.hours_mode, c.work_days, c.day_start, c.day_end, c.warn_percent, "
                        + "c.pause_when_waiting, c.updated_at, u.full_name AS updated_by FROM projects p "
                        + "LEFT JOIN sla_calendars c ON c.project_id = p.id "
                        + "LEFT JOIN users u ON u.id = c.updated_by_user_id WHERE p.id = ?", projectId);
        if (project.isEmpty()) {
            throw AccessService.notFound("Project");
        }
        Map<String, Object> r = project.get(0);
        Map<String, int[]> saved = new LinkedHashMap<>();
        jdbc.query("SELECT priority_code, first_response_minutes, resolution_minutes FROM sla_targets WHERE project_id = ?",
                rs -> {
                    saved.put(rs.getString("priority_code"),
                            new int[] {rs.getInt("first_response_minutes"), rs.getInt("resolution_minutes")});
                }, projectId);
        List<Target> targets = new ArrayList<>();
        Map<String, int[]> defaults = engine.defaultTargets();
        for (String p : SlaEngine.PRIORITIES) {
            int[] t = saved.getOrDefault(p, defaults.getOrDefault(p, SlaEngine.DEFAULT_TARGETS.get(p)));
            targets.add(new Target(p, t[0], t[1]));
        }
        List<Holiday> holidays = jdbc.query("SELECT holiday_date, name FROM sla_holidays WHERE project_id = ? ORDER BY holiday_date",
                (rs, n) -> new Holiday(rs.getDate("holiday_date").toLocalDate(), rs.getString("name")), projectId);
        Object updatedAt = r.get("updated_at");
        return new Settings(projectId, (String) r.get("project_name"), (String) r.get("time_zone"),
                r.get("hours_mode") == null ? "TWENTY_FOUR_SEVEN" : (String) r.get("hours_mode"),
                r.get("work_days") == null ? "MON,TUE,WED,THU,FRI" : (String) r.get("work_days"),
                r.get("day_start") == null ? "09:00" : hhmm(r.get("day_start")),
                r.get("day_end") == null ? "18:00" : hhmm(r.get("day_end")),
                r.get("warn_percent") == null ? 80 : ((Number) r.get("warn_percent")).intValue(),
                r.get("pause_when_waiting") == null || Boolean.TRUE.equals(r.get("pause_when_waiting")),
                targets, holidays,
                updatedAt instanceof Timestamp ts ? ts.toInstant() : null,
                (String) r.get("updated_by"));
    }

    // ================================================================ overview

    @Transactional(readOnly = true)
    public Overview overview(Long projectId, LocalDate from, LocalDate to) {
        AuthenticatedUser me = access.current();
        rbac.require(me, "SLA_VIEW", "SLA_MANAGE");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalDate f = from != null ? from : today.minusDays(29);
        LocalDate t = to != null ? to : today;
        if (f.isAfter(t)) {
            throw bad("'From' must not be after 'To'");
        }
        if (f.plusDays(MAX_RANGE_DAYS).isBefore(t)) {
            throw bad("Please choose at most " + MAX_RANGE_DAYS + " days");
        }

        StringBuilder where = new StringBuilder("WHERE p.status <> 'CLOSED'");
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
                return new Overview(Instant.now(), f.toString(), t.toString(), List.of(), List.of());
            }
            where.append(" AND t.project_id IN (").append("?,".repeat(visible.size() - 1)).append("?)");
            args.addAll(visible);
        }
        String inRange = "((t.created_at_source AT TIME ZONE p.time_zone)::date BETWEEN ? AND ?)";
        String openRecent = "(NOT " + SlaEngine.DONE + " AND t.created_at_source >= now() - interval '"
                + OPEN_LOOKBACK_DAYS + " days')";
        where.append(" AND (").append(inRange).append(" OR ").append(openRecent).append(")");
        args.add(Date.valueOf(f));
        args.add(Date.valueOf(t));

        Map<Long, SlaEngine.Policy> policies = engine.policies();
        List<SlaEngine.TicketRow> rows = engine.tickets(where.toString(), args.toArray());
        Instant now = Instant.now();

        Map<Long, long[]> counts = new LinkedHashMap<>(); // per project: 8 counters
        Map<Long, String> names = new LinkedHashMap<>();
        List<AttentionTicket> attention = new ArrayList<>();
        for (SlaEngine.TicketRow row : rows) {
            SlaEngine.Policy policy = policies.get(row.projectId());
            if (policy == null) {
                continue;
            }
            SlaEngine.Result r = engine.evaluate(row, policy, now);
            names.put(row.projectId(), row.projectName());
            long[] c = counts.computeIfAbsent(row.projectId(), k -> new long[8]);
            boolean createdInRange = row.createdAt() != null && inRange(row, policy, f, t);
            if (createdInRange && isFinal(r.responseState())) {
                c[0]++;
                if (SlaEngine.MET.equals(r.responseState())) {
                    c[1]++;
                }
            }
            if (createdInRange && !r.open() && isFinal(r.resolutionState())) {
                c[2]++;
                if (SlaEngine.MET.equals(r.resolutionState())) {
                    c[3]++;
                }
            }
            if (r.open()) {
                String worst = worst(r);
                switch (worst) {
                    case SlaEngine.BREACHED -> c[6]++;
                    case SlaEngine.AT_RISK -> c[5]++;
                    case SlaEngine.PAUSED -> c[7]++;
                    default -> c[4]++;
                }
                if (SlaEngine.BREACHED.equals(worst) || SlaEngine.AT_RISK.equals(worst)) {
                    boolean resolutionClock = r.resolutionState().equals(worst);
                    attention.add(new AttentionTicket(row.id(), row.number(), row.subject(), row.projectName(),
                            row.priority(), row.statusName(), row.assignee(), worst,
                            resolutionClock ? "RESOLUTION" : "FIRST_RESPONSE",
                            resolutionClock ? r.resolutionTarget() : r.responseTarget(),
                            resolutionClock ? r.resolutionElapsed() : r.responseElapsed(),
                            resolutionClock ? r.resolutionDue() : r.responseDue()));
                }
            }
        }
        List<ProjectSla> projects = new ArrayList<>();
        for (Map.Entry<Long, long[]> e : counts.entrySet()) {
            long[] c = e.getValue();
            SlaEngine.Policy p = policies.get(e.getKey());
            projects.add(new ProjectSla(e.getKey(), names.get(e.getKey()), p == null ? null : p.hoursMode(),
                    c[0], c[1], c[2], c[3], percent(c[1], c[0]), percent(c[3], c[2]), c[4], c[5], c[6], c[7]));
        }
        projects.sort(Comparator.comparing(ProjectSla::projectName, String.CASE_INSENSITIVE_ORDER));
        attention.sort(Comparator.comparing((AttentionTicket a) -> SlaEngine.BREACHED.equals(a.state()) ? 0 : 1)
                .thenComparing(AttentionTicket::dueAt, Comparator.nullsLast(Comparator.naturalOrder())));
        if (attention.size() > MAX_ATTENTION) {
            attention = new ArrayList<>(attention.subList(0, MAX_ATTENTION));
        }
        return new Overview(now, f.toString(), t.toString(), projects, attention);
    }

    // ================================================================ helpers

    private void requireLeadView(AuthenticatedUser me, Long projectId) {
        rbac.require(me, "SLA_VIEW", "SLA_MANAGE");
        if (!access.canSeeProject(me, projectId)) {
            throw AccessService.notFound("Project");
        }
    }

    /** Created between 'from' and 'to' (both included), in the project's own time zone. */
    private static boolean inRange(SlaEngine.TicketRow row, SlaEngine.Policy policy, LocalDate from, LocalDate to) {
        LocalDate d = row.createdAt().atZone(policy.zone()).toLocalDate();
        return !d.isBefore(from) && !d.isAfter(to);
    }

    private static boolean isFinal(String state) {
        return SlaEngine.MET.equals(state) || SlaEngine.BREACHED.equals(state);
    }

    /** The more serious of the two clocks for an open ticket. */
    private static String worst(SlaEngine.Result r) {
        List<String> order = List.of(SlaEngine.BREACHED, SlaEngine.AT_RISK, SlaEngine.ON_TRACK, SlaEngine.PAUSED);
        String a = r.resolutionState();
        String b = r.responseState();
        int ia = order.indexOf(a);
        int ib = order.indexOf(b);
        if (ia < 0) {
            return ib < 0 ? SlaEngine.ON_TRACK : b;
        }
        if (ib < 0) {
            return a;
        }
        return ia <= ib ? a : b;
    }

    private static Double percent(long part, long whole) {
        return whole == 0 ? null : Math.round(part * 1000.0 / whole) / 10.0;
    }

    private static String sortDays(String csv) {
        List<String> order = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
        Set<String> chosen = new HashSet<>(List.of(csv.split(",")));
        return String.join(",", order.stream().filter(chosen::contains).toList());
    }

    private static String hhmm(Object time) {
        String s = time.toString();
        return s.length() >= 5 ? s.substring(0, 5) : s;
    }

    private static String label(String priority) {
        return switch (priority) {
            case "CRITICAL" -> "Critical";
            case "HIGH" -> "High";
            case "MEDIUM" -> "Medium";
            default -> "Low";
        };
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}