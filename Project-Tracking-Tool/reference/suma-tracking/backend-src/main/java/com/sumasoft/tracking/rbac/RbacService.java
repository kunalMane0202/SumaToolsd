package com.sumasoft.tracking.rbac;

import com.sumasoft.tracking.audit.AuditService;
import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Role-based access control (Step 39).
 *
 * What a person may do = the permissions of their role:
 *  - SUPER_ADMIN: every permission, always (cannot be changed, so nobody can lock the tool).
 *  - A role changed by the Super Admin: exactly the permissions chosen for it (role_permissions).
 *  - Any other role: the defaults of the role (permission_defaults), or for a role created later,
 *    the defaults of its type (GROUP_PROJECT_MANAGER or GROUP_SUPPORT_USER).
 *
 * Permissions say WHAT a role may do. WHICH records stay limited by AccessService: people only see
 * the clients, projects and tickets they belong to (hidden records answer 404).
 */
@Service
public class RbacService {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final List<String> BUILT_IN = List.of("SUPER_ADMIN", "CLIENT_ADMIN", "PROJECT_MANAGER", "TEAM_LEADER",
            "L1_SUPPORT", "L2_SUPPORT", "L3_SUPPORT", "MANAGEMENT_VIEWER");

    private final AccessService access;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public RbacService(AccessService access, AuditService audit, DataSource dataSource) {
        this.access = access;
        this.audit = audit;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public record Permission(String code, String module, String name, String description, boolean extendable) {
    }

    public record MyPermissions(String roleCode, String roleName, boolean superAdmin, List<String> permissions) {
    }

    public record RoleColumn(String code, String name, String userGroup, boolean builtIn, boolean locked,
                             boolean customised, long activeUsers, List<String> permissions, List<String> defaults,
                             Instant updatedAt, String updatedBy) {
    }

    public record Matrix(List<Permission> permissions, List<RoleColumn> roles) {
    }

    public record SaveRequest(@NotNull(message = "Choose the permissions") List<String> permissions) {
    }

    private record Role(long id, String code, String name, String userGroup) {
    }

    // ================================================================ checks used by the guard and the services

    /** The permissions of a signed-in employee. */
    public Set<String> permissionsOf(long userId) {
        List<Role> r = jdbc.query("SELECT r.id, r.code, r.name, r.user_group FROM users u JOIN roles r ON r.id = u.role_id "
                + "WHERE u.id = ?", (rs, n) -> new Role(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("user_group")), userId);
        return r.isEmpty() ? Set.of() : effective(r.get(0));
    }

    public boolean has(AuthenticatedUser me, String... anyOf) {
        Set<String> mine = permissionsOf(me.userId());
        for (String p : anyOf) {
            if (mine.contains(p)) {
                return true;
            }
        }
        return false;
    }

    /** Refuses with 403 unless the employee has at least one of the permissions. */
    public void require(AuthenticatedUser me, String... anyOf) {
        if (!has(me, anyOf)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your role does not allow this action");
        }
    }

    @Transactional(readOnly = true)
    public MyPermissions mine() {
        AuthenticatedUser me = access.current();
        List<Role> r = jdbc.query("SELECT r.id, r.code, r.name, r.user_group FROM users u JOIN roles r ON r.id = u.role_id "
                + "WHERE u.id = ?", (rs, n) -> new Role(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("user_group")), me.userId());
        if (r.isEmpty()) {
            return new MyPermissions(null, null, false, List.of());
        }
        return new MyPermissions(r.get(0).code(), r.get(0).name(), SUPER_ADMIN.equals(r.get(0).code()),
                new ArrayList<>(new TreeSet<>(effective(r.get(0)))));
    }

    // ================================================================ Super Admin: the permission matrix

    @Transactional(readOnly = true)
    public Matrix matrix() {
        access.requireSuperAdmin(access.current());
        List<Permission> perms = allPermissions();
        List<RoleColumn> roles = new ArrayList<>();
        for (Role r : roles()) {
            Map<String, Object> meta = jdbc.queryForList("SELECT s.updated_at, u.full_name FROM role_permission_sets s "
                    + "LEFT JOIN users u ON u.id = s.updated_by_user_id WHERE s.role_id = ?", r.id()).stream().findFirst().orElse(null);
            Long users = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role_id = ? AND status = 'ACTIVE'", Long.class, r.id());
            boolean locked = SUPER_ADMIN.equals(r.code());
            roles.add(new RoleColumn(r.code(), r.name(), r.userGroup(), BUILT_IN.contains(r.code()), locked, meta != null,
                    users == null ? 0 : users, sorted(effective(r)), sorted(locked ? allCodes() : defaults(r)),
                    meta == null ? null : ((Timestamp) meta.get("updated_at")).toInstant(),
                    meta == null ? null : (String) meta.get("full_name")));
        }
        return new Matrix(perms, roles);
    }

    @Transactional
    public RoleColumn save(String roleCode, SaveRequest request) {
        AuthenticatedUser me = access.current();
        access.requireSuperAdmin(me);
        Role r = role(roleCode);
        if (SUPER_ADMIN.equals(r.code())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Super Admin always has every permission and cannot be changed");
        }
        Map<String, Permission> known = new LinkedHashMap<>();
        allPermissions().forEach(p -> known.put(p.code(), p));
        Set<String> defaults = defaults(r);
        Set<String> chosen = new LinkedHashSet<>();
        for (String code : request.permissions()) {
            Permission p = known.get(code);
            if (p == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown permission: " + code);
            }
            if (!p.extendable() && !defaults.contains(code)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\"" + p.name() + "\" can only be given to the roles "
                        + "that have it by default. The server's own rules for it are fixed.");
            }
            chosen.add(code);
        }
        Set<String> before = effective(r);
        jdbc.update("INSERT INTO role_permission_sets (role_id, updated_at, updated_by_user_id) VALUES (?, now(), ?) "
                + "ON CONFLICT (role_id) DO UPDATE SET updated_at = now(), updated_by_user_id = EXCLUDED.updated_by_user_id",
                r.id(), me.userId());
        jdbc.update("DELETE FROM role_permissions WHERE role_id = ?", r.id());
        for (String code : chosen) {
            jdbc.update("INSERT INTO role_permissions (role_id, permission_code, granted_by_user_id) VALUES (?, ?, ?)",
                    r.id(), code, me.userId());
        }
        audit.record("ROLE_PERMISSIONS_CHANGED", "ROLE", r.id(), null, null, facts(r, before), facts(r, chosen));
        return column(r.code());
    }

    @Transactional
    public RoleColumn reset(String roleCode) {
        AuthenticatedUser me = access.current();
        access.requireSuperAdmin(me);
        Role r = role(roleCode);
        if (SUPER_ADMIN.equals(r.code())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Super Admin always has every permission");
        }
        Set<String> before = effective(r);
        jdbc.update("DELETE FROM role_permissions WHERE role_id = ?", r.id());
        jdbc.update("DELETE FROM role_permission_sets WHERE role_id = ?", r.id());
        audit.record("ROLE_PERMISSIONS_RESET", "ROLE", r.id(), null, null, facts(r, before), facts(r, defaults(r)));
        return column(r.code());
    }

    // ================================================================ helpers

    private RoleColumn column(String code) {
        return matrix().roles().stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow();
    }

    private Set<String> effective(Role r) {
        if (SUPER_ADMIN.equals(r.code())) {
            return allCodes();
        }
        Boolean customised = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM role_permission_sets WHERE role_id = ?)",
                Boolean.class, r.id());
        if (Boolean.TRUE.equals(customised)) {
            return new LinkedHashSet<>(jdbc.queryForList("SELECT permission_code FROM role_permissions WHERE role_id = ?",
                    String.class, r.id()));
        }
        return defaults(r);
    }

    private Set<String> defaults(Role r) {
        String profile = BUILT_IN.contains(r.code()) ? r.code()
                : ("SUPPORT_USER".equals(r.userGroup()) ? "GROUP_SUPPORT_USER" : "GROUP_PROJECT_MANAGER");
        return new LinkedHashSet<>(jdbc.queryForList("SELECT permission_code FROM permission_defaults WHERE profile = ?",
                String.class, profile));
    }

    private Set<String> allCodes() {
        return new LinkedHashSet<>(jdbc.queryForList("SELECT code FROM permissions ORDER BY sort_order", String.class));
    }

    private List<Permission> allPermissions() {
        return jdbc.query("SELECT code, module, name, description, extendable FROM permissions ORDER BY sort_order",
                (rs, n) -> new Permission(rs.getString("code"), rs.getString("module"), rs.getString("name"),
                        rs.getString("description"), rs.getBoolean("extendable")));
    }

    private List<Role> roles() {
        return jdbc.query("SELECT id, code, name, user_group FROM roles ORDER BY sort_order, id",
                (rs, n) -> new Role(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("user_group")));
    }

    private Role role(String code) {
        List<Role> r = jdbc.query("SELECT id, code, name, user_group FROM roles WHERE code = ?",
                (rs, n) -> new Role(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("user_group")), code);
        if (r.isEmpty()) {
            throw AccessService.notFound("Role");
        }
        return r.get(0);
    }

    private static List<String> sorted(Set<String> s) {
        return new ArrayList<>(new TreeSet<>(s));
    }

    private static Map<String, Object> facts(Role r, Set<String> perms) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", r.code());
        m.put("permissions", String.join(", ", new TreeSet<>(perms)));
        return m;
    }
}