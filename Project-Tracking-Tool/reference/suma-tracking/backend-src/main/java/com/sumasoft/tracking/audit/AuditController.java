package com.sumasoft.tracking.audit;

import com.sumasoft.tracking.auth.AccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Web address (API) for reading the audit history. Super Admin only (restricted audit access).
 *   GET /api/audit                                  -> newest 100 records
 *   GET /api/audit?entityType=CLIENT&entityId=2     -> history of client 2
 *   GET /api/audit?projectId=3&limit=50             -> history touching project 3
 *   GET /api/audit?actorUserId=1                    -> what employee 1 did
 * There is NO address to change or delete audit records, on purpose.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;
    private final AccessService access;

    public AuditController(AuditService auditService, AccessService access) {
        this.auditService = auditService;
        this.access = access;
    }

    @GetMapping
    public List<AuditEntry> search(@RequestParam(required = false) String entityType,
                                   @RequestParam(required = false) Long entityId,
                                   @RequestParam(required = false) Long clientId,
                                   @RequestParam(required = false) Long projectId,
                                   @RequestParam(required = false) Long actorUserId,
                                   @RequestParam(defaultValue = "100") int limit) {
        access.requireSuperAdmin(access.current());
        return auditService.search(entityType, entityId, clientId, projectId, actorUserId, limit);
    }
}