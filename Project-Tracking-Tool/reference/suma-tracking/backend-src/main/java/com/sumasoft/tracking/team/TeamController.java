package com.sumasoft.tracking.team;

import jakarta.validation.Valid;
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
 * Web addresses (API) for project teams.
 *   GET  /api/projects/{projectId}/team                       -> current team
 *   GET  /api/projects/{projectId}/team?includeRemoved=true   -> team including removed people (history)
 *   PUT  /api/projects/{projectId}/team                       -> add someone, or change their job
 *   POST /api/projects/{projectId}/team/{userId}/remove       -> remove (history is kept)
 *   GET  /api/users/{userId}/projects                         -> all projects of one employee
 * NOTE: no login yet. Login and permissions are added in Phase 4.
 */
@RestController
@RequestMapping("/api")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping("/projects/{projectId}/team")
    public List<AssignmentResponse> listTeam(@PathVariable Long projectId,
                                             @RequestParam(defaultValue = "false") boolean includeRemoved) {
        return teamService.listTeam(projectId, includeRemoved);
    }

    @PutMapping("/projects/{projectId}/team")
    public AssignmentResponse assign(@PathVariable Long projectId, @Valid @RequestBody AssignmentRequest request) {
        return teamService.assign(projectId, request);
    }

    @PostMapping("/projects/{projectId}/team/{userId}/remove")
    public AssignmentResponse remove(@PathVariable Long projectId, @PathVariable Long userId) {
        return teamService.remove(projectId, userId);
    }

    @GetMapping("/users/{userId}/projects")
    public List<AssignmentResponse> listProjectsOfUser(@PathVariable Long userId) {
        return teamService.listProjectsOfUser(userId);
    }
}