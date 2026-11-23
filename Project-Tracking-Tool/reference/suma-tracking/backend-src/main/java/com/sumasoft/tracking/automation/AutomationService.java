package com.sumasoft.tracking.automation;

import com.sumasoft.tracking.audit.AuditService;
import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import com.sumasoft.tracking.notification.NotificationService;
import com.sumasoft.tracking.settings.SettingsService;
import com.sumasoft.tracking.sla.SlaEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Automation (Step 37). Runs every 5 minutes by itself, or when the Super Admin presses "Run now".
 * Rules (each can be switched on or off on the Automation page):
 *   SLA_ALERTS               about to break / broken SLA -> assignee + project leads (+ Super Admins for breaches)
 *   ASSIGNMENT_ALERTS        an open ticket was assigned to someone -> that person
 *   AUTO_ESCALATE_ON_BREACH  resolution SLA broken -> escalated, status Pending - L2, leads told
 *   AUTO_ASSIGN              new unassigned open ticket -> L1 member of the project with the fewest open tickets
 *   DAILY_SUMMARY            after 09:00 India time, yesterday's numbers to Super Admins and Project Managers
 * Every action is written to automation_actions (the execution log on the page). Each alert is sent once.
 * Only tickets of the last N days (Settings, default 30) are checked, so old history does not flood anyone.
 */
@Service
public class AutomationService {

    private static final Logger log = LoggerFactory.getLogger(AutomationService.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final String DONE = SlaEngine.DONE;

    private final AccessService access;
    private final AuditService audit;
    private final SlaEngine engine;
    private final NotificationService notifications;
    private final SettingsService settings;
    private final JdbcTemplate jdbc;

    public AutomationService(AccessService access, AuditService audit, SlaEngine engine,
                             NotificationService notifications, SettingsService settings, DataSource dataSource) {
        this.access = access;
        this.audit = audit;
        this.engine = engine;
        this.notifications = notifications;
        this.settings = settings;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public record Rule(String code, String name, String description, boolean enabled, Instant updatedAt, String updatedBy) {
    }

    public record RuleUpdate(@jakarta.validation.constraints.NotNull(message = "Say whether the rule is on or off") Boolean enabled) {
    }

    public record Run(Long id, String triggerType, String startedBy, Instant startedAt, Instant finishedAt, String status,
                      int ticketsChecked, int notificationsCreated, int ticketsEscalated, int ticketsAssigned,
                      String message) {
    }

    public record Action(Long id, String ruleCode, String ruleName, Long ticketId, String ticketNumber, String action,
                         String detail, Instant createdAt) {
    }

    /** Counters of one run. */
    private static final class Counters {
        final int limit;
        int checked;
        int notified;
        int escalated;
        int assigned;

        Counters(int limit) {
            this.limit = limit;
        }

        boolean full() {
            return notified >= limit;
        }
    }

    // ================================================================ page

    public List<Rule> rules() {
        access.requireSuperAdmin(access.current());
        return jdbc.query("SELECT r.code, r.name, r.description, r.enabled, r.updated_at, u.full_name FROM automation_rules r "
                        + "LEFT JOIN users u ON u.id = r.updated_by_user_id ORDER BY r.sort_order",
                (rs, n) -> new Rule(rs.getString("code"), rs.getString("name"), rs.getString("description"),
                        rs.getBoolean("enabled"), instant(rs.getTimestamp("updated_at")), rs.getString("full_name")));
    }

    public Rule updateRule(String code, RuleUpdate update) {
        AuthenticatedUser me = access.current();
        access.requireSuperAdmin(me);
        List<Boolean> before = jdbc.queryForList("SELECT enabled FROM automation_rules WHERE code = ?", Boolean.class, code);
        if (before.isEmpty()) {
            throw AccessService.notFound("Automation rule");
        }
        jdbc.update("UPDATE automation_rules SET enabled = ?, updated_at = now(), updated_by_user_id = ? WHERE code = ?",
                update.enabled(), me.userId(), code);
        audit.record("AUTOMATION_RULE_UPDATED", "AUTOMATION_RULE", null, null, null,
                facts(code, before.get(0)), facts(code, update.enabled()));
        return rules().stream().filter(r -> r.code().equals(code)).findFirst().orElseThrow();
    }

    public List<Run> runs() {
        access.requireSuperAdmin(access.current());
        return jdbc.query(RUN_SELECT + " ORDER BY r.started_at DESC, r.id DESC LIMIT 50", (rs, n) -> run(rs));
    }

    public List<Action> actions(Long runId) {
        access.requireSuperAdmin(access.current());
        return jdbc.query("SELECT a.id, a.rule_code, ru.name AS rule_name, a.ticket_id, a.action, a.detail, a.created_at, "
                        + "COALESCE(NULLIF(BTRIM(t.external_ticket_id), ''), 'TK-' || lpad(t.id::text, 6, '0')) AS ticket_number "
                        + "FROM automation_actions a JOIN automation_rules ru ON ru.code = a.rule_code "
                        + "LEFT JOIN tickets t ON t.id = a.ticket_id WHERE a.run_id = ? ORDER BY a.id LIMIT 1000",
                (rs, n) -> new Action(rs.getLong("id"), rs.getString("rule_code"), rs.getString("rule_name"),
                        (Long) rs.getObject("ticket_id", Long.class),
                        rs.getObject("ticket_id") == null ? null : rs.getString("ticket_number"),
                        rs.getString("action"), rs.getString("detail"), instant(rs.getTimestamp("created_at"))),
                runId);
    }

    public Run runNow() {
        AuthenticatedUser me = access.current();
        access.requireSuperAdmin(me);
        Run run = execute("MANUAL", me.userId());
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Automation is already running. Try again in a minute.");
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("runId", run.id());
        summary.put("status", run.status());
        summary.put("ticketsChecked", run.ticketsChecked());
        summary.put("notifications", run.notificationsCreated());
        summary.put("escalated", run.ticketsEscalated());
        summary.put("assigned", run.ticketsAssigned());
        audit.record("AUTOMATION_RUN", "AUTOMATION_RUN", run.id(), null, null, null, summary);
        return run;
    }

    /** Called by the scheduler. */
    public void runScheduled() {
        Run run = execute("SCHEDULE", null);
        if (run != null && "FAILED".equals(run.status())) {
            log.warn("Automation run {} finished with problems: {}", run.id(), run.message());
        }
    }

    // ================================================================ one run

    private Run execute(String trigger, Long userId) {
        if (!RUNNING.compareAndSet(false, true)) {
            return null;
        }
        try {
            Long runId = jdbc.queryForObject("INSERT INTO automation_runs (trigger_type, started_by_user_id) "
                    + "VALUES (?, ?) RETURNING id", Long.class, trigger, userId);
            Map<String, Boolean> on = new HashMap<>();
            jdbc.query("SELECT code, enabled FROM automation_rules", rs -> {
                on.put(rs.getString("code"), rs.getBoolean("enabled"));
            });
            Counters c = new Counters(settings.number("automation.maxNotificationsPerRun"));
            List<String> problems = new ArrayList<>();
            boolean slaAlerts = on.getOrDefault("SLA_ALERTS", false);
            boolean autoEscalate = on.getOrDefault("AUTO_ESCALATE_ON_BREACH", false);
            if (slaAlerts || autoEscalate) {
                step(problems, "SLA", () -> slaStep(runId, c, slaAlerts, autoEscalate));
            }
            if (on.getOrDefault("AUTO_ASSIGN", false)) {
                step(problems, "Auto-assign", () -> autoAssignStep(runId, c));
            }
            if (on.getOrDefault("ASSIGNMENT_ALERTS", false)) {
                step(problems, "Assignment alerts", () -> assignmentStep(runId, c));
            }
            if (on.getOrDefault("DAILY_SUMMARY", false)) {
                step(problems, "Daily summary", () -> dailySummaryStep(runId, c));
            }
            String message = problems.isEmpty()
                    ? (c.full() ? "Notification limit of " + c.limit + " reached; the rest go out next run" : null)
                    : String.join(" | ", problems);
            jdbc.update("UPDATE automation_runs SET finished_at = now(), status = ?, tickets_checked = ?, "
                            + "notifications_created = ?, tickets_escalated = ?, tickets_assigned = ?, message = ? WHERE id = ?",
                    problems.isEmpty() ? "SUCCESS" : "FAILED", c.checked, c.notified, c.escalated, c.assigned,
                    message == null ? null : clip(message, 1000), runId);
            return jdbc.query(RUN_SELECT + " WHERE r.id = ?", (rs, n) -> run(rs), runId).get(0);
        } finally {
            RUNNING.set(false);
        }
    }

    private static void step(List<String> problems, String name, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            log.warn("Automation step '{}' failed", name, e);
            problems.add(name + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    // ---------------------------------------------------------------- SLA alerts + auto-escalation

    private void slaStep(Long runId, Counters c, boolean alerts, boolean escalate) {
        Map<Long, SlaEngine.Policy> policies = engine.policies();
        List<SlaEngine.TicketRow> rows = engine.tickets("WHERE NOT " + DONE + " AND p.status = 'ACTIVE' "
                + "AND t.created_at_source >= now() - make_interval(days => ?) ORDER BY t.created_at_source",
                settings.number("automation.alertLookbackDays"));
        c.checked += rows.size();
        Instant now = Instant.now();
        for (SlaEngine.TicketRow t : rows) {
            SlaEngine.Policy policy = policies.get(t.projectId());
            if (policy == null) {
                continue;
            }
            SlaEngine.Result r = engine.evaluate(t, policy, now);
            if (alerts) {
                alertFor(runId, c, t, r.responseState(), "RESPONSE", "first response",
                        r.responseTarget(), r.responseElapsed());
                alertFor(runId, c, t, r.resolutionState(), "RESOLUTION", "resolution",
                        r.resolutionTarget(), r.resolutionElapsed());
            }
            if (escalate && SlaEngine.BREACHED.equals(r.resolutionState()) && !t.escalated()
                    && !"PENDING_L2".equals(t.statusCode())
                    && ("OPEN".equals(t.statusGroup()) || "IN_PROGRESS".equals(t.statusGroup()))) {
                escalate(runId, c, t, r);
            }
        }
    }

    private void alertFor(Long runId, Counters c, SlaEngine.TicketRow t, String state, String clock, String clockLabel,
                          long target, long elapsed) {
        boolean breached = SlaEngine.BREACHED.equals(state);
        boolean atRisk = SlaEngine.AT_RISK.equals(state);
        if ((!breached && !atRisk) || c.full()) {
            return;
        }
        String kind = clock + (breached ? "_BREACHED" : "_AT_RISK");
        if (jdbc.update("INSERT INTO sla_alerts_sent (ticket_id, alert_kind) VALUES (?, ?) ON CONFLICT DO NOTHING",
                t.id(), kind) == 0) {
            return; // already told
        }
        if (breached) { // no "about to break" message after the breach message
            jdbc.update("INSERT INTO sla_alerts_sent (ticket_id, alert_kind) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    t.id(), clock + "_AT_RISK");
        }
        Set<Long> to = new LinkedHashSet<>();
        if (t.assigneeUserId() != null) {
            to.add(t.assigneeUserId());
        }
        to.addAll(notifications.projectLeads(t.projectId()));
        if (breached) {
            to.addAll(notifications.superAdmins());
        }
        String title = (breached ? "SLA breached: " : "SLA at risk: ") + t.projectName() + " ticket " + t.number();
        String body = cap(clockLabel) + " target " + duration(target) + " (" + cap(t.priority().toLowerCase(Locale.ROOT))
                + " priority), " + duration(elapsed) + " used. Status: " + t.statusName()
                + ". Assignee: " + (t.assignee() == null ? "nobody" : t.assignee()) + "."
                + (t.subject() == null || t.subject().isBlank() ? "" : " Subject: " + t.subject());
        int sent = notifications.send(to, "SLA_" + kind, title, body, t.id(), t.projectId());
        c.notified += sent;
        logAction(runId, "SLA_ALERTS", t.id(), kind, "Told " + sent + " people. " + duration(elapsed) + " of " + duration(target));
    }

    private void escalate(Long runId, Counters c, SlaEngine.TicketRow t, SlaEngine.Result r) {
        int changed = jdbc.update("UPDATE tickets SET escalated = TRUE, "
                + "status_id = (SELECT id FROM ticket_statuses WHERE code = 'PENDING_L2'), "
                + "first_response_at = COALESCE(first_response_at, now()), updated_at_source = now(), updated_at = now(), "
                + "version = version + 1 WHERE id = ? AND NOT escalated", t.id());
        if (changed == 0) {
            return;
        }
        c.escalated++;
        logAction(runId, "AUTO_ESCALATE_ON_BREACH", t.id(), "ESCALATED",
                "Resolution SLA broken (" + duration(r.resolutionElapsed()) + " of " + duration(r.resolutionTarget())
                        + "). Status " + t.statusCode() + " -> PENDING_L2");
        if (!c.full()) {
            Set<Long> to = new LinkedHashSet<>(notifications.projectLeads(t.projectId()));
            if (t.assigneeUserId() != null) {
                to.add(t.assigneeUserId());
            }
            c.notified += notifications.send(to, "AUTO_ESCALATED",
                    "Auto-escalated to Tier 2: " + t.projectName() + " ticket " + t.number(),
                    "The resolution SLA was broken, so the ticket was escalated and moved to Pending - L2.",
                    t.id(), t.projectId());
        }
    }

    // ---------------------------------------------------------------- auto-assign

    private void autoAssignStep(Long runId, Counters c) {
        List<Map<String, Object>> tickets = jdbc.queryForList("SELECT t.id, t.project_id, p.project_name, p.time_zone, "
                + "COALESCE(NULLIF(BTRIM(t.external_ticket_id), ''), 'TK-' || lpad(t.id::text, 6, '0')) AS ticket_number, "
                + "t.subject FROM tickets t JOIN projects p ON p.id = t.project_id "
                + "JOIN ticket_statuses s ON s.id = t.status_id "
                + "WHERE s.status_group = 'OPEN' AND t.assignee_user_id IS NULL "
                + "AND COALESCE(BTRIM(t.assignee_name_raw), '') = '' AND p.status = 'ACTIVE' "
                + "AND t.created_at_source >= now() - interval '7 days' ORDER BY t.created_at_source LIMIT 200");
        for (Map<String, Object> t : tickets) {
            long ticketId = ((Number) t.get("id")).longValue();
            long projectId = ((Number) t.get("project_id")).longValue();
            String today = LocalDate.now(zone((String) t.get("time_zone"))).getDayOfWeek().name().substring(0, 3);
            List<Map<String, Object>> candidates = jdbc.queryForList("SELECT u.id, u.full_name, u.weekly_off, "
                    + "(SELECT COUNT(*) FROM tickets t2 JOIN ticket_statuses s2 ON s2.id = t2.status_id "
                    + " WHERE t2.assignee_user_id = u.id AND s2.status_group NOT IN ('RESOLVED', 'CLOSED')) AS open_count "
                    + "FROM project_assignments pa JOIN users u ON u.id = pa.user_id "
                    + "WHERE pa.project_id = ? AND pa.active AND pa.project_role = 'MEMBER' AND pa.support_level = 'L1' "
                    + "AND u.status = 'ACTIVE' ORDER BY open_count, u.id", projectId);
            Map<String, Object> pick = candidates.stream()
                    .filter(u -> u.get("weekly_off") == null || !((String) u.get("weekly_off")).contains(today))
                    .findFirst().orElse(null);
            if (pick == null) {
                continue;
            }
            long userId = ((Number) pick.get("id")).longValue();
            int changed = jdbc.update("UPDATE tickets SET assignee_user_id = ?, updated_at = now(), version = version + 1 "
                    + "WHERE id = ? AND assignee_user_id IS NULL", userId, ticketId);
            if (changed == 0) {
                continue;
            }
            c.assigned++;
            markSeen(ticketId, userId);
            logAction(runId, "AUTO_ASSIGN", ticketId, "ASSIGNED", "Assigned to " + pick.get("full_name")
                    + " (" + pick.get("open_count") + " open tickets before this one)");
            if (!c.full()) {
                c.notified += notifications.send(List.of(userId), "TICKET_ASSIGNED",
                        "New ticket for you: " + t.get("project_name") + " ticket " + t.get("ticket_number"),
                        "Assigned automatically (you had the fewest open tickets)."
                                + (t.get("subject") == null ? "" : " Subject: " + t.get("subject")),
                        ticketId, projectId);
            }
        }
    }

    // ---------------------------------------------------------------- assignment alerts

    private void assignmentStep(Long runId, Counters c) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT t.id, t.project_id, t.assignee_user_id, t.subject, "
                + "p.project_name, u.full_name, "
                + "COALESCE(NULLIF(BTRIM(t.external_ticket_id), ''), 'TK-' || lpad(t.id::text, 6, '0')) AS ticket_number "
                + "FROM tickets t JOIN projects p ON p.id = t.project_id JOIN ticket_statuses s ON s.id = t.status_id "
                + "JOIN users u ON u.id = t.assignee_user_id "
                + "LEFT JOIN ticket_assignment_seen a ON a.ticket_id = t.id "
                + "WHERE NOT " + DONE + " AND t.updated_at >= now() - interval '7 days' "
                + "AND (a.user_id IS NULL OR a.user_id <> t.assignee_user_id) ORDER BY t.updated_at LIMIT 300");
        for (Map<String, Object> t : rows) {
            if (c.full()) {
                return;
            }
            long ticketId = ((Number) t.get("id")).longValue();
            long userId = ((Number) t.get("assignee_user_id")).longValue();
            markSeen(ticketId, userId);
            int sent = notifications.send(List.of(userId), "TICKET_ASSIGNED",
                    "Ticket assigned to you: " + t.get("project_name") + " ticket " + t.get("ticket_number"),
                    t.get("subject") == null ? "Open it in Ticket Management." : "Subject: " + t.get("subject"),
                    ticketId, ((Number) t.get("project_id")).longValue());
            c.notified += sent;
            logAction(runId, "ASSIGNMENT_ALERTS", ticketId, "TOLD_ASSIGNEE", "Told " + t.get("full_name"));
        }
    }

    // ---------------------------------------------------------------- daily summary

    private void dailySummaryStep(Long runId, Counters c) {
        LocalDateTime nowIndia = LocalDateTime.now(INDIA);
        if (nowIndia.getHour() < settings.number("automation.dailySummaryHour")) {
            return;
        }
        LocalDate today = nowIndia.toLocalDate();
        Boolean sentToday = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM automation_actions WHERE rule_code = 'DAILY_SUMMARY' "
                + "AND (created_at AT TIME ZONE 'Asia/Kolkata')::date = ?)", Boolean.class, Date.valueOf(today));
        if (Boolean.TRUE.equals(sentToday)) {
            return;
        }
        LocalDate yesterday = today.minusDays(1);
        Date y = Date.valueOf(yesterday);
        String local = "AT TIME ZONE 'Asia/Kolkata')::date";
        List<Map<String, Object>> perProject = jdbc.queryForList("SELECT p.id, p.project_name, "
                + "COUNT(t.id) FILTER (WHERE (t.created_at_source " + local + " = ?) AS created, "
                + "COUNT(t.id) FILTER (WHERE " + DONE + " AND (COALESCE(t.closed_at, t.resolved_at) " + local + " = ?) AS done, "
                + "COUNT(t.id) FILTER (WHERE t.id IS NOT NULL AND NOT " + DONE + ") AS open_now, "
                + "(SELECT COUNT(*) FROM sla_alerts_sent a JOIN tickets t3 ON t3.id = a.ticket_id WHERE t3.project_id = p.id "
                + " AND a.alert_kind = 'RESOLUTION_BREACHED' AND (a.sent_at " + local + " = ?) AS breaches "
                + "FROM projects p LEFT JOIN tickets t ON t.project_id = p.id "
                + "LEFT JOIN ticket_statuses s ON s.id = t.status_id "
                + "WHERE p.status = 'ACTIVE' GROUP BY p.id, p.project_name ORDER BY p.project_name", y, y, y);
        Map<Long, String> lines = new LinkedHashMap<>();
        for (Map<String, Object> p : perProject) {
            long created = ((Number) p.get("created")).longValue();
            long done = ((Number) p.get("done")).longValue();
            long open = ((Number) p.get("open_now")).longValue();
            long breaches = ((Number) p.get("breaches")).longValue();
            if (created + done + open + breaches == 0) {
                continue;
            }
            lines.put(((Number) p.get("id")).longValue(), p.get("project_name") + ": " + created + " new, " + done
                    + " done, " + open + " open now, " + breaches + " SLA breach" + (breaches == 1 ? "" : "es"));
        }
        String title = settings.text("company.name") + ": daily summary for " + DAY.format(yesterday);
        int sent = 0;
        if (!lines.isEmpty()) {
            String all = String.join(" | ", lines.values());
            sent += notifications.send(notifications.superAdmins(), "DAILY_SUMMARY", title, all, null, null);
            Set<Long> managers = new LinkedHashSet<>(notifications.allProjectManagers());
            managers.removeAll(notifications.superAdmins());
            for (Map.Entry<Long, Set<Long>> m : notifications.projectsLedBy(managers).entrySet()) {
                List<String> mine = m.getValue().stream().filter(lines::containsKey).map(lines::get).toList();
                if (!mine.isEmpty()) {
                    sent += notifications.send(List.of(m.getKey()), "DAILY_SUMMARY", title, String.join(" | ", mine), null, null);
                }
            }
        }
        c.notified += sent;
        logAction(runId, "DAILY_SUMMARY", null, "SUMMARY_SENT",
                lines.isEmpty() ? "No ticket activity yesterday; nothing sent" : "Sent to " + sent + " people");
    }

    // ================================================================ helpers

    private static final String RUN_SELECT = "SELECT r.id, r.trigger_type, u.full_name, r.started_at, r.finished_at, r.status, "
            + "r.tickets_checked, r.notifications_created, r.tickets_escalated, r.tickets_assigned, r.message "
            + "FROM automation_runs r LEFT JOIN users u ON u.id = r.started_by_user_id";

    private static Run run(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Run(rs.getLong("id"), rs.getString("trigger_type"), rs.getString("full_name"),
                instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("finished_at")), rs.getString("status"),
                rs.getInt("tickets_checked"), rs.getInt("notifications_created"), rs.getInt("tickets_escalated"),
                rs.getInt("tickets_assigned"), rs.getString("message"));
    }

    private void markSeen(long ticketId, long userId) {
        jdbc.update("INSERT INTO ticket_assignment_seen (ticket_id, user_id) VALUES (?, ?) "
                + "ON CONFLICT (ticket_id) DO UPDATE SET user_id = EXCLUDED.user_id, seen_at = now()", ticketId, userId);
    }

    private void logAction(Long runId, String rule, Long ticketId, String action, String detail) {
        jdbc.update("INSERT INTO automation_actions (run_id, rule_code, ticket_id, action, detail) VALUES (?, ?, ?, ?, ?)",
                runId, rule, ticketId, action, clip(detail, 500));
    }

    static String duration(long minutes) {
        long d = minutes / 1440;
        long h = (minutes % 1440) / 60;
        long m = minutes % 60;
        if (d > 0) {
            return d + "d " + h + "h";
        }
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static ZoneId zone(String name) {
        try {
            return name == null || name.isBlank() ? INDIA : ZoneId.of(name);
        } catch (java.time.DateTimeException e) {
            return INDIA;
        }
    }

    private static Map<String, Object> facts(String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key, value);
        return m;
    }

    private static String clip(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}