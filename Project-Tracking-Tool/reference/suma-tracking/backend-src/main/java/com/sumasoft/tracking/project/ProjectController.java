package com.sumasoft.tracking.project;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Web addresses (API) for projects.
 *   GET  /api/projects                  -> all projects
 *   GET  /api/projects?clientId=3       -> projects of client 3
 *   GET  /api/projects/{id}             -> one project
 *   POST /api/projects                  -> create a project
 *   PUT  /api/projects/{id}             -> edit a project
 *   POST /api/projects/{id}/activate    -> make ACTIVE
 *   POST /api/projects/{id}/deactivate  -> make INACTIVE
 *   POST /api/projects/{id}/close       -> make CLOSED (nothing is deleted)
 * NOTE: no login yet. Login and permissions are added in Phase 4.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectResponse> listProjects(@RequestParam(required = false) Long clientId) {
        return projectService.listProjects(clientId);
    }

    @GetMapping("/{id}")
    public ProjectResponse getProject(@PathVariable Long id) {
        return projectService.getProject(id);
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody ProjectRequest request) {
        ProjectResponse created = projectService.createProject(request);
        return ResponseEntity.created(URI.create("/api/projects/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ProjectResponse updateProject(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return projectService.updateProject(id, request);
    }

    @PostMapping("/{id}/activate")
    public ProjectResponse activateProject(@PathVariable Long id) {
        return projectService.activateProject(id);
    }

    @PostMapping("/{id}/deactivate")
    public ProjectResponse deactivateProject(@PathVariable Long id) {
        return projectService.deactivateProject(id);
    }

    @PostMapping("/{id}/close")
    public ProjectResponse closeProject(@PathVariable Long id) {
        return projectService.closeProject(id);
    }
}