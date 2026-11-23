package com.sumasoft.tracking.admin;

import com.sumasoft.tracking.admin.AdminClientDtos.ClientForm;
import com.sumasoft.tracking.admin.AdminClientDtos.ClientRow;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Web addresses for the Client Management screen (Super Admin only).
 *   GET    /api/admin/clients?includeRemoved=false
 *   POST   /api/admin/clients            -> add
 *   PUT    /api/admin/clients/{id}       -> update
 *   DELETE /api/admin/clients/{id}       -> remove (kept in the database as INACTIVE, with all its tickets)
 *   POST   /api/admin/clients/{id}/restore
 */
@RestController
@RequestMapping("/api/admin/clients")
public class AdminClientController {

    private final AdminClientService service;

    public AdminClientController(AdminClientService service) {
        this.service = service;
    }

    @GetMapping
    public List<ClientRow> list(@RequestParam(defaultValue = "false") boolean includeRemoved) {
        return service.list(includeRemoved);
    }

    @PostMapping
    public ClientRow add(@Valid @RequestBody ClientForm form) {
        return service.add(form);
    }

    @PutMapping("/{id}")
    public ClientRow update(@PathVariable Long id, @Valid @RequestBody ClientForm form) {
        return service.update(id, form);
    }

    @DeleteMapping("/{id}")
    public ClientRow remove(@PathVariable Long id) {
        return service.remove(id);
    }

    @PostMapping("/{id}/restore")
    public ClientRow restore(@PathVariable Long id) {
        return service.restore(id);
    }
}