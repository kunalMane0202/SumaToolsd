package com.sumasoft.tracking.admin;

import com.sumasoft.tracking.audit.AuditService;
import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import com.sumasoft.tracking.rbac.RbacService;
import com.sumasoft.tracking.notification.NotificationService;
import com.sumasoft.tracking.user.RoleResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Admin features of Step 35 (FRD: client management, user and team management, ticket management).
 *
 *  - Account manager of a client: one employee per client (clients.account_manager_id, Flyway V3).
 *  - New roles (Super Admin only). A new role works like one of the two existing role types:
 *      PROJECT_MANAGER = a lead type (Project Lead, Manager, Team Leader...): sees the projects they are on,
 *                        and leads them when added to a team as Project Manager / Team Leader.
 *      SUPPORT_USER    = an agent type (L1 / L2 / L3 like): sees only the projects they are on.
 *    Super Admin, Management Viewer and Client Admin keep their special powers and cannot be copied.
 *  - Workload: per employee, tickets assigned to them (only tickets linked to the employee).
 *  - Escalate: marks the ticket "escalated" and sets status Pending - L2 (Tier 2), with history and audit.
 */
@Service
public class AdminService {

    private static final Set<String> BUILT_IN_ROLES = Set.of("SUPER_ADMIN", "MANAGEMENT_VIEWER", "CLIENT_ADMIN",
            "PROJECT_MANAGER", "TEAM_LEADER", "L1_SUPPORT", "L2_SUPPORT", "L3_SUPPORT");

    private final AccessService access;
    private final AuditService audit;
    private final NotificationService notifications;
    private final RbacService rbac;
    private final JdbcTemplate jdbc;

    public AdminService(AccessService access, AuditService audit, NotificationService notifications, RbacService rbac,
                        DataSource dataSource) {
        this.access = access;
        this.audit = audit;
        this.notifications = notifications;
        this.rbac = rbac;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ================================================================ account managers

    public record AccountManagerResponse(Long clientId, Long userId, String fullName) {
    }

    public record AccountManagerRequest(Long userId) {
    }

    @Transactional(readOnly = true)
    public List<AccountManagerResponse> accountManagers() {
        AuthenticatedUser me = access.current();
        List<AccountManagerResponse> all = jdbc.query("SELECT c.id, u.id AS user_id, u.full_name FROM clients c "
                        + "LEFT JOIN users u ON u.id = c.account_manager_id ORDER BY c.id",
                (rs, n) -> new AccountManagerResponse(rs.getLong("id"), (Long) rs.getObject("user_id", Long.class),
                        rs.getString("full_name")));
        if (access.seesEverything(me)) {
            return all;
        }
        return all.stream().filter(a -> access.canSeeClient(me, a.clientId())).toList();
    }

    @Transactional
    public AccountManagerResponse setAccountManager(Long clientId, AccountManagerRequest request) {
        access.requireSuperAdmin(access.current());
        List<Long> before = jdbc.queryForList("SELECT account_manager_id FROM clients WHERE id = ?", Long.class, clientId);
        if (before.isEmpty()) {
            throw AccessService.notFound("Client");
        }
        Long userId = request == null ? null : request.userId();
        String name = null;
        if (userId != null) {
            List<String> names = jdbc.queryForList("SELECT full_name FROM users WHERE id = ? AND status = 'ACTIVE'",
                    String.class, userId);
            if (names.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The account manager must be an active employee");
            }
            name = names.get(0);
        }
        jdbc.update("UPDATE clients SET account_manager_id = ?, updated_at = now(), version = version + 1 WHERE id = ?",
                userId, clientId);
        audit.record("CLIENT_ACCOUNT_MANAGER_SET", "CLIENT", clientId, clientId, null,
                facts("accountManagerUserId", before.get(0)), facts("accountManagerUserId", userId));
        return new AccountManagerResponse(clientId, userId, name);
    }

    // ================================================================ roles

    public record RoleCreateRequest(
            @NotBlank(message = "Role name is required")
            @Size(max = 100, message = "Role name must be at most 100 characters")
            String name,
            @NotBlank(message = "Role type is required: PROJECT_MANAGER (lead) or SUPPORT_USER (agent)")
            @Pattern(regexp = "^(PROJECT_MANAGER|SUPPORT_USER)$",
                     message = "Role type must be PROJECT_MANAGER (lead) or SUPPORT_USER (agent)")
            String userGroup,
            @Size(max = 1000, message = "Description must be at most 1000 characters")
            String description
    ) {
    }

    public record RoleUpdateRequest(
            @NotBlank(message = "Role name is required")
            @Size(max = 100, message = "Role name must be at most 100 characters")
            String name,
            @Size(max = 1000, message = "Description must be at most 1000 characters")
            String description
    ) {
    }

    @Transactional
    public RoleResponse createRole(RoleCreateRequest r) {
        access.requireSuperAdmin(access.current());
        String name = r.name().trim();
        String code = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (code.isEmpty() || !Character.isLetter(code.charAt(0))) {
            code = "ROLE_" + code;
        }
        if (code.length() > 40) {
            code = code.substring(0, 40);
        }
        if (code.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role name is too short");
        }
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE code = ? OR lower(name) = lower(?)",
                Integer.class, code, name);
        if (exists != null && exists > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A role with this name already exists");
        }
        jdbc.update("INSERT INTO roles (code, name, user_group, description, sort_order) "
                        + "VALUES (?, ?, ?, ?, (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM roles))",
                code, name, r.userGroup(), blankToNull(r.description()));
        RoleResponse created = new RoleResponse(code, name, r.userGroup(), blankToNull(r.description()));
        audit.record("ROLE_CREATED", "ROLE", null, null, null, null, created);
        return created;
    }

    @Transactional
    public RoleResponse updateRole(String code, RoleUpdateRequest r) {
        access.requireSuperAdmin(access.current());
        String c = code.trim().toUpperCase(Locale.ROOT);
        if (BUILT_IN_ROLES.contains(c)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The built-in roles cannot be changed");
        }
        List<RoleResponse> found = jdbc.query("SELECT code, name, user_group, description FROM roles WHERE code = ?",
                (rs, n) -> new RoleResponse(rs.getString("code"), rs.getString("name"), rs.getString("user_group"),
                        rs.getString("description")), c);
        if (found.isEmpty()) {
            throw AccessService.notFound("Role");
        }
        jdbc.update("UPDATE roles SET name = ?, description = ? WHERE code = ?", r.name().trim(), blankToNull(r.description()), c);
        RoleResponse after = new RoleResponse(c, r.name().trim(), found.get(0).userGroup(), blankToNull(r.description()));
        audit.record("ROLE_UPDATED", "ROLE", null, null, null, found.get(0), after);
        return after;
    }

    // ================================================================ workload

    public record WorkloadRow(Long userId, String fullName, String employeeCode, String roleName, String status,
                              String projects, long open, long waiting, long doneLast30Days, long escalatedOpen,
                              Long oldestOpenMinutes) {
    }

    @Transactional(readOnly = true)
    public List<WorkloadRow> workload() {
        AuthenticatedUser me = access.current();
        rbac.require(me, "WORKLOAD_VIEW", "USER_MANAGE");
        StringBuilder ticketFilter = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (!access.seesEverything(me)) {
            Set<Long> visible = access.visibleProjectIds(me);
            if (visible.isEmpty()) {
                return List.of();
            }
            ticketFilter.append(" AND t.project_id IN (").append("?,".repeat(visible.size() - 1)).append("?)");
            args.addAll(visible);
        }
        String sql = "SELECT u.id, u.full_name, u.employee_code, r.name AS role_name, u.status, "
                + "(SELECT string_agg(p.project_name, ', ' ORDER BY p.project_name) FROM project_assignments pa "
                + "  JOIN projects p ON p.id = pa.project_id WHERE pa.user_id = u.id AND pa.active) AS projects, "
                + "COUNT(t.id) FILTER (WHERE s.status_group IN ('OPEN', 'IN_PROGRESS')) AS open_c, "
                + "COUNT(t.id) FILTER (WHERE s.status_group = 'PENDING') AS waiting_c, "
                + "COUNT(t.id) FILTER (WHERE s.status_group IN ('RESOLVED', 'CLOSED') "
                + "  AND COALESCE(t.closed_at, t.resolved_at) >= now() - interval '30 days') AS done30, "
                + "COUNT(t.id) FILTER (WHERE t.escalated AND s.status_group NOT IN ('RESOLVED', 'CLOSED')) AS esc_open, "
                + "(EXTRACT(EPOCH FROM (now() - MIN(t.created_at_source) FILTER "
                + "  (WHERE s.status_group NOT IN ('RESOLVED', 'CLOSED')))) / 60)::bigint AS oldest "
                + "FROM users u JOIN roles r ON r.id = u.role_id "
                + "LEFT JOIN tickets t ON t.assignee_user_id = u.id" + ticketFilter
                + " LEFT JOIN ticket_statuses s ON s.id = t.status_id "
                + "GROUP BY u.id, u.full_name, u.employee_code, r.name, u.status ORDER BY u.full_name";
        List<WorkloadRow> rows = jdbc.query(sql, (rs, n) -> new WorkloadRow(rs.getLong("id"), rs.getString("full_name"),
                rs.getString("employee_code"), rs.getString("role_name"), rs.getString("status"),
                rs.getString("projects"), rs.getLong("open_c"), rs.getLong("waiting_c"), rs.getLong("done30"),
                rs.getLong("esc_open"), (Long) rs.getObject("oldest", Long.class)), args.toArray());
        if (access.seesEverything(me)) {
            return rows;
        }
        // Leads only see people who share a project with them.
        Set<Long> visibleProjects = access.visibleProjectIds(me);
        List<Long> people = jdbc.queryForList("SELECT DISTINCT user_id FROM project_assignments WHERE active AND project_id IN ("
                + "?,".repeat(visibleProjects.size() - 1) + "?)", Long.class, visibleProjects.toArray());
        return rows.stream().filter(r -> people.contains(r.userId())).toList();
    }

    // ================================================================ escalate a ticket

    public record EscalateRequest(@Size(max = 500, message = "Note must be at most 500 characters") String note) {
    }

    @Transactional
    public void escalate(Long ticketId, EscalateRequest request) {
        AuthenticatedUser me = access.current();
        List<Map<String, Object>> found = jdbc.queryForList("SELECT t.project_id, t.client_id, t.escalated, "
                + "t.assignee_user_id, s.code, s.status_group FROM tickets t JOIN ticket_statuses s ON s.id = t.status_id "
                + "WHERE t.id = ?", ticketId);
        if (found.isEmpty()) {
            throw AccessService.notFound("Ticket");
        }
        Map<String, Object> t = found.get(0);
        long projectId = ((Number) t.get("project_id")).longValue();
        long clientId = ((Number) t.get("client_id")).longValue();
        if (!access.canSeeProject(me, projectId)) {
            throw AccessService.notFound("Ticket");
        }
        Object assignee = t.get("assignee_user_id");
        boolean assignedToMe = assignee != null && ((Number) assignee).longValue() == me.userId();
        if (!isLead(me, projectId, clientId) && !assignedToMe) {
            throw AccessService.forbidden();
        }
        String group = (String) t.get("status_group");
        if ("RESOLVED".equals(group) || "CLOSED".equals(group)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A finished ticket cannot be escalated. Reopen it first");
        }
        if (Boolean.TRUE.equals(t.get("escalated")) && "PENDING_L2".equals(t.get("code"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The ticket is already escalated to Tier 2");
        }
        String note = request == null || request.note() == null || request.note().isBlank() ? null : request.note().trim();
        jdbc.update("UPDATE tickets SET escalated = TRUE, status_id = (SELECT id FROM ticket_statuses WHERE code = 'PENDING_L2'), "
                + "first_response_at = COALESCE(first_response_at, now()), updated_at_source = now(), updated_at = now(), "
                + "version = version + 1 WHERE id = ?", ticketId);
        if (!Boolean.TRUE.equals(t.get("escalated"))) {
            history(ticketId, "UPDATED", "escalated", "false", "true", me.userId(), note);
        }
        if (!"PENDING_L2".equals(t.get("code"))) {
            history(ticketId, "STATUS_CHANGED", "status", (String) t.get("code"), "PENDING_L2", me.userId(), note);
        }
        audit.record("TICKET_ESCALATED", "TICKET", ticketId, clientId, projectId,
                facts("status", t.get("code")), facts("status", "PENDING_L2"));
        // Step 37: tell the project leads and the assignee (not the person who escalated it)
        Set<Long> to = new java.util.LinkedHashSet<>(notifications.projectLeads(projectId));
        if (assignee != null) {
            to.add(((Number) assignee).longValue());
        }
        to.remove(me.userId());
        String number = jdbc.queryForObject("SELECT COALESCE(NULLIF(BTRIM(t.external_ticket_id), ''), 'TK-' || "
                + "lpad(t.id::text, 6, '0')) || ' (' || p.project_name || ')' FROM tickets t JOIN projects p ON p.id = t.project_id "
                + "WHERE t.id = ?", String.class, ticketId);
        notifications.send(to, "TICKET_ESCALATED", "Ticket escalated to Tier 2: " + number,
                note == null ? "Moved to Pending - L2." : "Moved to Pending - L2. Note: " + note, ticketId, projectId);
    }

    private void history(Long ticketId, String event, String field, String oldValue, String newValue, Long userId, String note) {
        jdbc.update("INSERT INTO ticket_history (ticket_id, event_type, field_name, old_value, new_value, "
                + "changed_by_user_id, source, note) VALUES (?, ?, ?, ?, ?, ?, 'USER', ?)",
                ticketId, event, field, oldValue, newValue, userId, note);
    }

    /** Super Admin, Client Admin of the client, or Project Manager / Team Leader on the project. */
    private boolean isLead(AuthenticatedUser me, long projectId, long clientId) {
        if (access.isSuperAdmin(me) || access.isClientAdminOf(me, clientId)) {
            return true;
        }
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM project_assignments WHERE project_id = ? AND user_id = ? "
                + "AND active AND project_role IN ('PROJECT_MANAGER', 'TEAM_LEADER')", Integer.class, projectId, me.userId());
        return n != null && n > 0;
    }

    private static Map<String, Object> facts(String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key, value);
        return m;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}