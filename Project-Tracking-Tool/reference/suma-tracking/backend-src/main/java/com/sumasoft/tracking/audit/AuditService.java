package com.sumasoft.tracking.audit;

import com.sumasoft.tracking.auth.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes and reads the audit history (table audit.audit_log, Flyway V5).
 *
 * record(...) is called inside the same database transaction as the change itself:
 * if the change is cancelled, its audit record is cancelled too, so history always matches reality.
 * Rows can only be added; the database blocks any change or delete.
 */
@Service
public class AuditService {

    private static final String INSERT_SQL =
            "INSERT INTO audit.audit_log (actor_user_id, actor_email, action, entity_type, entity_id, "
            + "client_id, project_id, source, source_reference, old_value, new_value, ip_address) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?)";

    private final JdbcTemplate jdbc;

    public AuditService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * Record one change made by the logged-in person.
     *
     * @param action     what happened, e.g. CLIENT_UPDATED
     * @param entityType CLIENT, PROJECT, USER, PROJECT_ASSIGNMENT, ...
     * @param entityId   the id of the changed record
     * @param clientId   affected client (may be null)
     * @param projectId  affected project (may be null)
     * @param before     snapshot before the change (null when created)
     * @param after      snapshot after the change (null when nothing to show)
     */
    @Transactional
    public void record(String action, String entityType, Long entityId, Long clientId, Long projectId,
                       Object before, Object after) {
        recordWithSource(action, entityType, entityId, clientId, projectId, before, after, "USER", null);
    }

    /** Same as record(...), but for imports, integrations or the system itself. */
    @Transactional
    public void recordWithSource(String action, String entityType, Long entityId, Long clientId, Long projectId,
                                 Object before, Object after, String source, String sourceReference) {
        AuthenticatedUser actor = currentUserOrNull();
        jdbc.update(INSERT_SQL,
                actor == null ? null : actor.userId(),
                actor == null ? null : actor.email(),
                action,
                entityType,
                entityId,
                clientId,
                projectId,
                source,
                sourceReference,
                AuditJson.toJson(before),
                AuditJson.toJson(after),
                currentIpOrNull());
    }

    /**
     * Read the history, newest first. All filters are optional.
     * limit: at most 500 rows per request.
     */
    @Transactional(readOnly = true)
    public List<AuditEntry> search(String entityType, Long entityId, Long clientId, Long projectId,
                                   Long actorUserId, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, occurred_at, actor_user_id, actor_email, action, entity_type, entity_id, client_id, "
                + "project_id, source, source_reference, old_value::text AS old_value, new_value::text AS new_value, "
                + "ip_address FROM audit.audit_log WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (entityType != null && !entityType.isBlank()) {
            sql.append(" AND entity_type = ?");
            args.add(entityType.trim().toUpperCase());
        }
        if (entityId != null) {
            sql.append(" AND entity_id = ?");
            args.add(entityId);
        }
        if (clientId != null) {
            sql.append(" AND client_id = ?");
            args.add(clientId);
        }
        if (projectId != null) {
            sql.append(" AND project_id = ?");
            args.add(projectId);
        }
        if (actorUserId != null) {
            sql.append(" AND actor_user_id = ?");
            args.add(actorUserId);
        }
        int safeLimit = Math.max(1, Math.min(limit, 500));
        sql.append(" ORDER BY id DESC LIMIT ").append(safeLimit);
        return jdbc.query(sql.toString(), this::mapRow, args.toArray());
    }

    private AuditEntry mapRow(ResultSet rs, int rowNumber) throws SQLException {
        OffsetDateTime occurred = rs.getObject("occurred_at", OffsetDateTime.class);
        return new AuditEntry(
                rs.getObject("id", Long.class),
                occurred == null ? null : occurred.toInstant(),
                rs.getObject("actor_user_id", Long.class),
                rs.getString("actor_email"),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getObject("entity_id", Long.class),
                rs.getObject("client_id", Long.class),
                rs.getObject("project_id", Long.class),
                rs.getString("source"),
                rs.getString("source_reference"),
                rs.getString("old_value"),
                rs.getString("new_value"),
                rs.getString("ip_address"));
    }

    private static AuthenticatedUser currentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }

    private static String currentIpOrNull() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            String ip = request.getRemoteAddr();
            return ip == null ? null : (ip.length() <= 45 ? ip : ip.substring(0, 45));
        }
        return null;
    }
}