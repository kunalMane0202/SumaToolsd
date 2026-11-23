package com.sumasoft.tracking.dashboard;

import com.sumasoft.tracking.dashboard.DashboardDtos.DashboardResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Web address (API) for the dashboard.
 *   GET /api/dashboard?projectId=3&from=2026-10-01&to=2026-11-05
 *   All filters are optional. Dates are the ticket creation dates in the project's time zone.
 *   Each person only sees the projects they may see.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public DashboardResponse dashboard(@RequestParam(required = false) Long projectId,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.dashboard(projectId, from, to);
    }
}