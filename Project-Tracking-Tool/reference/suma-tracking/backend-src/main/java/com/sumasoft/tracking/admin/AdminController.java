package com.sumasoft.tracking.admin;

import com.sumasoft.tracking.admin.AdminService.AccountManagerRequest;
import com.sumasoft.tracking.admin.AdminService.AccountManagerResponse;
import com.sumasoft.tracking.admin.AdminService.EscalateRequest;
import com.sumasoft.tracking.admin.AdminService.RoleCreateRequest;
import com.sumasoft.tracking.admin.AdminService.RoleUpdateRequest;
import com.sumasoft.tracking.admin.AdminService.WorkloadRow;
import com.sumasoft.tracking.user.RoleResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Web addresses (API) added in Step 35 for the admin screens.
 *   GET  /api/clients/account-managers          -> who manages which client
 *   PUT  /api/clients/{id}/account-manager      -> {"userId": 5}  (null = nobody)          Super Admin
 *   POST /api/roles                             -> {"name":"Project Lead","userGroup":"PROJECT_MANAGER","description":"..."}
 *   PUT  /api/roles/{code}                      -> {"name":"...","description":"..."}      Super Admin
 *   GET  /api/workload                          -> open / waiting / done tickets per employee
 *   POST /api/tickets/{id}/escalate             -> {"note":"..."}  marks escalated, status Pending - L2
 */
@RestController
@RequestMapping("/api")
public class AdminController {

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/clients/account-managers")
    public List<AccountManagerResponse> accountManagers() {
        return service.accountManagers();
    }

    @PutMapping("/clients/{id}/account-manager")
    public AccountManagerResponse setAccountManager(@PathVariable Long id, @RequestBody AccountManagerRequest request) {
        return service.setAccountManager(id, request);
    }

    @PostMapping("/roles")
    public RoleResponse createRole(@Valid @RequestBody RoleCreateRequest request) {
        return service.createRole(request);
    }

    @PutMapping("/roles/{code}")
    public RoleResponse updateRole(@PathVariable String code, @Valid @RequestBody RoleUpdateRequest request) {
        return service.updateRole(code, request);
    }

    @GetMapping("/workload")
    public List<WorkloadRow> workload() {
        return service.workload();
    }

    @PostMapping("/tickets/{id}/escalate")
    public void escalate(@PathVariable Long id, @Valid @RequestBody EscalateRequest request) {
        service.escalate(id, request);
    }
}