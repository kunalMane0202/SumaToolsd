package com.sumasoft.tracking.notification;

import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-app notifications (Step 37). Email comes later, once an approved mail service is chosen.
 *  - Each employee sees only their own notifications.
 *  - Notifications are never deleted; "read" only marks them as seen.
 *  - Only ACTIVE employees receive new notifications.
 */
@Service
public class NotificationService {

    private static final int MAX_LIST = 200;

    private final AccessService access;
    private final JdbcTemplate jdbc;

    public NotificationService(AccessService access, DataSource dataSource) {
        this.access = access;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public record NotificationRow(Long id, String kind, String title, String body, Long ticketId, String ticketNumber,
                                  String projectName, Instant createdAt, boolean read) {
    }

    public record UnreadCount(long count) {
    }

    // ------------------------------------------------------------ for the bell / Notifications page

    @Transactional(readOnly = true)
    public List<NotificationRow> mine(boolean unreadOnly) {
        AuthenticatedUser me = access.current();
        return jdbc.query("SELECT n.id, n.kind, n.title, n.body, n.ticket_id, n.created_at, n.read_at, p.project_name, "
                        + "COALESCE(NULLIF(BTRIM(t.external_ticket_id), ''), 'TK-' || lpad(t.id::text, 6, '0')) AS ticket_number "
                        + "FROM notifications n LEFT JOIN tickets t ON t.id = n.ticket_id "
                        + "LEFT JOIN projects p ON p.id = n.project_id WHERE n.user_id = ?"
                        + (unreadOnly ? " AND n.read_at IS NULL" : "")
                        + " ORDER BY n.created_at DESC, n.id DESC LIMIT " + MAX_LIST,
                (rs, i) -> new NotificationRow(rs.getLong("id"), rs.getString("kind"), rs.getString("title"),
                        rs.getString("body"), (Long) rs.getObject("ticket_id", Long.class),
                        rs.getObject("ticket_id") == null ? null : rs.getString("ticket_number"),
                        rs.getString("project_name"), rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("read_at") != null),
                me.userId());
    }

    @Transactional(readOnly = true)
    public UnreadCount unreadCount() {
        AuthenticatedUser me = access.current();
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_at IS NULL",
                Long.class, me.userId());
        return new UnreadCount(n == null ? 0 : n);
    }

    @Transactional
    public void markRead(Long id) {
        AuthenticatedUser me = access.current();
        int changed = jdbc.update("UPDATE notifications SET read_at = COALESCE(read_at, now()) WHERE id = ? AND user_id = ?",
                id, me.userId());
        if (changed == 0) {
            throw AccessService.notFound("Notification");
        }
    }

    @Transactional
    public UnreadCount markAllRead() {
        AuthenticatedUser me = access.current();
        jdbc.update("UPDATE notifications SET read_at = now() WHERE user_id = ? AND read_at IS NULL", me.userId());
        return new UnreadCount(0);
    }

    // ------------------------------------------------------------ used by Automation and other services

    /** Sends one notification to each ACTIVE employee in the list. Returns how many were created. */
    public int send(Collection<Long> userIds, String kind, String title, String body, Long ticketId, Long projectId) {
        int created = 0;
        for (Long userId : new LinkedHashSet<>(userIds)) {
            if (userId == null) {
                continue;
            }
            created += jdbc.update("INSERT INTO notifications (user_id, kind, title, body, ticket_id, project_id) "
                            + "SELECT u.id, ?, ?, ?, ?, ? FROM users u WHERE u.id = ? AND u.status = 'ACTIVE'",
                    kind, clip(title, 200), clip(body, 1000), ticketId, projectId, userId);
        }
        return created;
    }

    /** Active Project Managers and Team Leaders of a project. */
    public Set<Long> projectLeads(long projectId) {
        return new LinkedHashSet<>(jdbc.queryForList("SELECT pa.user_id FROM project_assignments pa "
                + "JOIN users u ON u.id = pa.user_id WHERE pa.project_id = ? AND pa.active AND u.status = 'ACTIVE' "
                + "AND pa.project_role IN ('PROJECT_MANAGER', 'TEAM_LEADER')", Long.class, projectId));
    }

    /** Active Project Managers of any project. */
    public Set<Long> allProjectManagers() {
        return new LinkedHashSet<>(jdbc.queryForList("SELECT DISTINCT pa.user_id FROM project_assignments pa "
                + "JOIN users u ON u.id = pa.user_id WHERE pa.active AND u.status = 'ACTIVE' "
                + "AND pa.project_role = 'PROJECT_MANAGER'", Long.class));
    }

    /** Active Super Admins. */
    public Set<Long> superAdmins() {
        return new LinkedHashSet<>(jdbc.queryForList("SELECT u.id FROM users u JOIN roles r ON r.id = u.role_id "
                + "WHERE r.code = 'SUPER_ADMIN' AND u.status = 'ACTIVE'", Long.class));
    }

    /** Projects each Project Manager leads: user id -> project ids. */
    public Map<Long, Set<Long>> projectsLedBy(Collection<Long> userIds) {
        Map<Long, Set<Long>> result = new java.util.HashMap<>();
        for (Long u : userIds) {
            result.put(u, new LinkedHashSet<>(jdbc.queryForList("SELECT project_id FROM project_assignments "
                    + "WHERE user_id = ? AND active AND project_role IN ('PROJECT_MANAGER', 'TEAM_LEADER')", Long.class, u)));
        }
        return result;
    }

    static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}