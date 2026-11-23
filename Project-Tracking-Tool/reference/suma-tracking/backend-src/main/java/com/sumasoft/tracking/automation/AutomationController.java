package com.sumasoft.tracking.automation;

import com.sumasoft.tracking.automation.AutomationService.Action;
import com.sumasoft.tracking.automation.AutomationService.Rule;
import com.sumasoft.tracking.automation.AutomationService.RuleUpdate;
import com.sumasoft.tracking.automation.AutomationService.Run;
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
 * Automation page (Step 37, Super Admin only).
 *   GET  /api/automation/rules
 *   PUT  /api/automation/rules/{code}       { "enabled": true }
 *   POST /api/automation/run                run all switched-on rules now
 *   GET  /api/automation/runs               last 50 runs
 *   GET  /api/automation/runs/{id}/actions  what one run did
 */
@RestController
@RequestMapping("/api/automation")
public class AutomationController {

    private final AutomationService service;

    public AutomationController(AutomationService service) {
        this.service = service;
    }

    @GetMapping("/rules")
    public List<Rule> rules() {
        return service.rules();
    }

    @PutMapping("/rules/{code}")
    public Rule update(@PathVariable String code, @Valid @RequestBody RuleUpdate update) {
        return service.updateRule(code, update);
    }

    @PostMapping("/run")
    public Run runNow() {
        return service.runNow();
    }

    @GetMapping("/runs")
    public List<Run> runs() {
        return service.runs();
    }

    @GetMapping("/runs/{id}/actions")
    public List<Action> actions(@PathVariable Long id) {
        return service.actions(id);
    }
}