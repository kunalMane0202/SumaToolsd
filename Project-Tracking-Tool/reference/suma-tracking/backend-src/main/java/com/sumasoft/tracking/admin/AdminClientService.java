package com.sumasoft.tracking.admin;

import com.sumasoft.tracking.admin.AdminClientDtos.ClientForm;
import com.sumasoft.tracking.admin.AdminClientDtos.ClientRow;
import com.sumasoft.tracking.audit.AuditService;
import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import com.sumasoft.tracking.rbac.RbacService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client Management screen (Step 35, roles in Step 39).
 *  - CLIENT_MANAGE (Super Admin): every client; add, edit, delete, restore, account managers.
 *  - CLIENT_EDIT_ASSIGNED (Client Admin): only the clients given to them; edit details and add projects,
 *    but not delete them or change their account manager. Other clients stay hidden (404).
 *  - Add / update: Name, Designation, Company Email ID, Contact Number, Project Name, Account Manager.
 *  - "Project Name": when it is a new name for this client, the project is created (ACTIVE, India time).
 *  - Account Manager "Yes" needs an ACTIVE employee; "No" removes the account manager.
 *  - Delete = the client is set to INACTIVE and hidden from the list. Nothing is erased, because
 *    its projects, tickets and history must stay (FR-021). "Restore" brings it back.
 *  - Every change is written to the audit history.
 */
@Service
public class AdminClientService {

    private final AccessService access;
    private final AuditService audit;
    private final RbacService rbac;
    private final JdbcTemplate jdbc;

    public AdminClientService(AccessService access, AuditService audit, RbacService rbac, DataSource dataSource) {
        this.access = access;
        this.audit = audit;
        this.rbac = rbac;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    private static final String SELECT = "SELECT c.id, c.client_name, c.designation, c.email, c.contact_number, c.status, "
            + "c.account_manager_id, u.full_name AS manager_name, "
            + "(SELECT string_agg(p.project_name, '|' ORDER BY p.project_name) FROM projects p WHERE p.client_id = c.id) AS projects "
            + "FROM clients c LEFT JOIN users u ON u.id = c.account_manager_id";

    @Transactional(readOnly = true)
    public List<ClientRow> list(boolean includeRemoved) {
        AuthenticatedUser me = access.current();
        rbac.require(me, "CLIENT_MANAGE", "CLIENT_EDIT_ASSIGNED");
        String sql = SELECT + (includeRemoved ? "" : " WHERE c.status = 'ACTIVE'") + " ORDER BY c.id";
        List<ClientRow> rows = jdbc.query(sql, (rs, n) -> row(rs));
        if (rbac.has(me, "CLIENT_MANAGE")) {
            return rows;
        }
        return rows.stream().filter(r -> access.isClientAdminOf(me, r.id())).toList();
    }

    @Transactional
    public ClientRow add(ClientForm f) {
        rbac.require(access.current(), "CLIENT_MANAGE");
        String name = f.name().trim();
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM clients WHERE lower(client_name) = lower(?))", Boolean.class, name))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A client with this name already exists");
        }
        Long managerId = managerOf(f);
        Long id = jdbc.queryForObject("INSERT INTO clients (client_name, designation, email, contact_number, account_manager_id) "
                        + "VALUES (?, ?, ?, ?, ?) RETURNING id", Long.class,
                name, clean(f.designation()), clean(f.email()), clean(f.contactNumber()), managerId);
        addProjectIfNew(id, f.projectName());
        ClientRow after = get(id);
        audit.record("CLIENT_CREATED", "CLIENT", id, id, null, null, snapshot(after));
        return after;
    }

    @Transactional
    public ClientRow update(Long id, ClientForm f) {
        AuthenticatedUser me = access.current();
        boolean all = rbac.has(me, "CLIENT_MANAGE");
        if (!all) {
            rbac.require(me, "CLIENT_EDIT_ASSIGNED");
            if (!access.isClientAdminOf(me, id)) {
                throw AccessService.notFound("Client");
            }
        }
        ClientRow before = get(id);
        String name = f.name().trim();
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM clients WHERE lower(client_name) = lower(?) AND id <> ?)", Boolean.class, name, id))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another client already uses this name");
        }
        // a Client Admin cannot change who the account manager is
        Long managerId = all ? managerOf(f) : before.accountManagerUserId();
        jdbc.update("UPDATE clients SET client_name = ?, designation = ?, email = ?, contact_number = ?, "
                        + "account_manager_id = ?, updated_at = now(), version = version + 1 WHERE id = ?",
                name, clean(f.designation()), clean(f.email()), clean(f.contactNumber()), managerId, id);
        addProjectIfNew(id, f.projectName());
        ClientRow after = get(id);
        audit.record("CLIENT_UPDATED", "CLIENT", id, id, null, snapshot(before), snapshot(after));
        return after;
    }

    @Transactional
    public ClientRow remove(Long id) {
        return setStatus(id, "INACTIVE", "CLIENT_DEACTIVATED");
    }

    @Transactional
    public ClientRow restore(Long id) {
        return setStatus(id, "ACTIVE", "CLIENT_ACTIVATED");
    }

    private ClientRow setStatus(Long id, String status, String action) {
        rbac.require(access.current(), "CLIENT_MANAGE");
        ClientRow before = get(id);
        jdbc.update("UPDATE clients SET status = ?, updated_at = now(), version = version + 1 WHERE id = ?", status, id);
        ClientRow after = get(id);
        audit.record(action, "CLIENT", id, id, null, snapshot(before), snapshot(after));
        return after;
    }

    private ClientRow get(Long id) {
        List<ClientRow> rows = jdbc.query(SELECT + " WHERE c.id = ?", (rs, n) -> row(rs), id);
        if (rows.isEmpty()) {
            throw AccessService.notFound("Client");
        }
        return rows.get(0);
    }

    /** Account Manager: Yes needs an active employee, No clears it. */
    private Long managerOf(ClientForm f) {
        if (!Boolean.TRUE.equals(f.accountManagerAssigned())) {
            return null;
        }
        if (f.accountManagerUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose the account manager");
        }
        Boolean ok = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM users WHERE id = ? AND status = 'ACTIVE')",
                Boolean.class, f.accountManagerUserId());
        if (!Boolean.TRUE.equals(ok)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The account manager must be an active employee");
        }
        return f.accountManagerUserId();
    }

    private void addProjectIfNew(Long clientId, String projectName) {
        String p = clean(projectName);
        if (p == null) {
            return;
        }
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM projects WHERE client_id = ? AND lower(project_name) = lower(?))",
                Boolean.class, clientId, p);
        if (Boolean.TRUE.equals(exists)) {
            return;
        }
        Long projectId = jdbc.queryForObject("INSERT INTO projects (client_id, project_name) VALUES (?, ?) RETURNING id",
                Long.class, clientId, p);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("projectName", p);
        after.put("clientId", clientId);
        audit.record("PROJECT_CREATED", "PROJECT", projectId, clientId, projectId, null, after);
    }

    private static ClientRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        long id = rs.getLong("id");
        Long managerId = rs.getObject("account_manager_id") == null ? null : rs.getLong("account_manager_id");
        String projects = rs.getString("projects");
        return new ClientRow(id, String.format("CL-%04d", id), rs.getString("client_name"), rs.getString("designation"),
                rs.getString("email"), rs.getString("contact_number"), rs.getString("status"),
                managerId != null, managerId, rs.getString("manager_name"),
                projects == null ? List.of() : new ArrayList<>(Arrays.asList(projects.split("\\|"))));
    }

    private static Map<String, Object> snapshot(ClientRow r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", r.name());
        m.put("designation", r.designation());
        m.put("email", r.email());
        m.put("contactNumber", r.contactNumber());
        m.put("status", r.status());
        m.put("accountManagerUserId", r.accountManagerUserId());
        m.put("projects", String.join(", ", r.projects()));
        return m;
    }

    private static String clean(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}