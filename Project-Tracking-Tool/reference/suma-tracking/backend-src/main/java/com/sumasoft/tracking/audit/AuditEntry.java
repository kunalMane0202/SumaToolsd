package com.sumasoft.tracking.audit;

import java.time.Instant;

/**
 * One audit record, as shown by GET /api/audit.
 * oldValue / newValue are JSON text ("before" and "after" the change).
 */
public record AuditEntry(
        Long id,
        Instant occurredAt,
        Long actorUserId,
        String actorEmail,
        String action,
        String entityType,
        Long entityId,
        Long clientId,
        Long projectId,
        String source,
        String sourceReference,
        String oldValue,
        String newValue,
        String ipAddress
) {
}