package com.sumasoft.tracking.sla;

import com.sumasoft.tracking.sla.SlaDtos.Overview;
import com.sumasoft.tracking.sla.SlaDtos.Settings;
import com.sumasoft.tracking.sla.SlaDtos.SettingsForm;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * SLA Management (Step 37).
 *   GET /api/sla/overview?projectId=&from=&to=   SLA % and the open tickets that need attention
 *   GET /api/sla/settings/{projectId}             support hours, targets, holidays of a project
 *   PUT /api/sla/settings/{projectId}             change them (Super Admin)
 */
@RestController
@RequestMapping("/api/sla")
public class SlaController {

    private final SlaService service;

    public SlaController(SlaService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public Overview overview(@RequestParam(required = false) Long projectId,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.overview(projectId, from, to);
    }

    @GetMapping("/settings/{projectId}")
    public Settings settings(@PathVariable Long projectId) {
        return service.settings(projectId);
    }

    @PutMapping("/settings/{projectId}")
    public Settings save(@PathVariable Long projectId, @Valid @RequestBody SettingsForm form) {
        return service.save(projectId, form);
    }
}