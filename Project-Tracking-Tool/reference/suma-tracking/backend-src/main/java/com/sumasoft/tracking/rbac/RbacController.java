package com.sumasoft.tracking.rbac;

import com.sumasoft.tracking.rbac.RbacService.Matrix;
import com.sumasoft.tracking.rbac.RbacService.MyPermissions;
import com.sumasoft.tracking.rbac.RbacService.RoleColumn;
import com.sumasoft.tracking.rbac.RbacService.SaveRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Roles and permissions (Step 39).
 *   GET  /api/rbac/me                          my role and permissions (everyone signed in)
 *   GET  /api/rbac/matrix                      every role x every permission (Super Admin)
 *   PUT  /api/rbac/roles/{code}/permissions    choose a role's permissions (Super Admin)
 *   POST /api/rbac/roles/{code}/reset          back to the role's defaults (Super Admin)
 */
@RestController
@RequestMapping("/api/rbac")
public class RbacController {

    private final RbacService service;

    public RbacController(RbacService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public MyPermissions me() {
        return service.mine();
    }

    @GetMapping("/matrix")
    public Matrix matrix() {
        return service.matrix();
    }

    @PutMapping("/roles/{code}/permissions")
    public RoleColumn save(@PathVariable String code, @Valid @RequestBody SaveRequest request) {
        return service.save(code, request);
    }

    @PostMapping("/roles/{code}/reset")
    public RoleColumn reset(@PathVariable String code) {
        return service.reset(code);
    }
}