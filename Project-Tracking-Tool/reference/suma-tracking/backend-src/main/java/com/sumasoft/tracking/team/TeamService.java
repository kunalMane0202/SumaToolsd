package com.sumasoft.tracking.team;

import com.sumasoft.tracking.audit.AuditService;
import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import com.sumasoft.tracking.project.Project;
import com.sumasoft.tracking.project.ProjectRepository;
import com.sumasoft.tracking.user.AppUser;
import com.sumasoft.tracking.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The business rules for project teams live here.
 *  - Only ACTIVE projects and ACTIVE employees can be assigned.
 *  - A Management Viewer is read-only and cannot be put on a team.
 *  - PROJECT_MANAGER / TEAM_LEADER on a project need a Super Admin or Project Manager type user.
 *  - MEMBER needs a support level (L1 / L2 / L3).
 *  - A project has at most one active Project Manager.
 *  - Removing someone keeps the record (active = false).
 * Permissions (Step 25):
 *  - see a team: anyone who may see the project
 *  - change a team: Super Admin, Client Admin of the client, the project's own Project Manager
 *  - projects of one employee: Super Admin / Management Viewer / yourself = all of them;
 *    others = only projects they may see, and only for people on their projects
 * Audit (Step 26): every add / change / removal is recorded with before and after values.
 */
@Service
public class TeamService {

    private final ProjectAssignmentRepository assignmentRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AccessService access;
    private final AuditService audit;

    public TeamService(ProjectAssignmentRepository assignmentRepository,
                       ProjectRepository projectRepository,
                       UserRepository userRepository,
                       AccessService access,
                       AuditService audit) {
        this.assignmentRepository = assignmentRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> listTeam(Long projectId, boolean includeRemoved) {
        findVisibleProject(access.current(), projectId);
        List<ProjectAssignment> rows = includeRemoved
                ? assignmentRepository.findByProjectIdOrderByIdAsc(projectId)
                : assignmentRepository.findByProjectIdAndActiveTrueOrderByIdAsc(projectId);
        return rows.stream().map(AssignmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> listProjectsOfUser(Long userId) {
        AuthenticatedUser me = access.current();
        if (access.seesEverything(me) || me.userId().equals(userId)) {
            findUser(userId);
            return assignmentRepository.findByUserIdOrderByIdAsc(userId).stream()
                    .map(AssignmentResponse::from).toList();
        }
        Set<Long> myProjects = access.visibleProjectIds(me);
        List<ProjectAssignment> theirs = assignmentRepository.findByUserIdOrderByIdAsc(userId).stream()
                .filter(a -> myProjects.contains(a.getProject().getId()))
                .toList();
        if (access.isSupportUser(me) || theirs.isEmpty()) {
            throw AccessService.notFound("Employee");
        }
        return theirs.stream().map(AssignmentResponse::from).toList();
    }

    /** Add someone to the team, or change their job on it (also brings back a removed person). */
    @Transactional
    public AssignmentResponse assign(Long projectId, AssignmentRequest request) {
        Project project = findManageableProject(projectId);
        if (!"ACTIVE".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Team changes are allowed only on ACTIVE projects. This project is " + project.getStatus());
        }
        AppUser user = findUser(request.userId());
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This employee is not active");
        }

        String projectRole = request.projectRole().trim().toUpperCase(Locale.ROOT);
        String supportLevel = request.supportLevel() == null || request.supportLevel().isBlank()
                ? null : request.supportLevel().trim().toUpperCase(Locale.ROOT);
        String roleCode = user.getRole().getCode();
        String userGroup = user.getRole().getUserGroup();

        if ("MANAGEMENT_VIEWER".equals(roleCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A Management Viewer is read-only and cannot be added to a project team");
        }
        boolean leadJob = "PROJECT_MANAGER".equals(projectRole) || "TEAM_LEADER".equals(projectRole);
        if (leadJob && !("SUPER_ADMIN".equals(userGroup) || "PROJECT_MANAGER".equals(userGroup))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only Super Admin or Project Manager type employees can be " + projectRole + " on a project");
        }
        if ("MEMBER".equals(projectRole) && supportLevel == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A MEMBER needs a support level: L1, L2 or L3");
        }
        if ("PROJECT_MANAGER".equals(projectRole)
                && assignmentRepository.existsByProjectIdAndProjectRoleAndActiveTrueAndUserIdNot(
                        projectId, "PROJECT_MANAGER", user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This project already has an active Project Manager. Remove them first");
        }

        ProjectAssignment assignment = assignmentRepository.findByProjectIdAndUserId(projectId, user.getId())
                .orElse(null);
        AssignmentResponse before = assignment == null ? null : AssignmentResponse.from(assignment);
        if (assignment == null) {
            assignment = ProjectAssignment.create(project, user, projectRole, supportLevel);
        } else {
            assignment.change(projectRole, supportLevel);
        }
        AssignmentResponse after = AssignmentResponse.from(assignmentRepository.saveAndFlush(assignment));
        audit.record(before == null ? "TEAM_MEMBER_ADDED" : "TEAM_MEMBER_CHANGED", "PROJECT_ASSIGNMENT",
                after.id(), project.getClient().getId(), projectId, before, after);
        return after;
    }

    /** Remove someone from the team. The record is kept (active = false). */
    @Transactional
    public AssignmentResponse remove(Long projectId, Long userId) {
        Project project = findManageableProject(projectId);
        ProjectAssignment assignment = assignmentRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "This employee is not on this project's team"));
        AssignmentResponse before = AssignmentResponse.from(assignment);
        assignment.remove();
        AssignmentResponse after = AssignmentResponse.from(assignmentRepository.saveAndFlush(assignment));
        audit.record("TEAM_MEMBER_REMOVED", "PROJECT_ASSIGNMENT", after.id(), project.getClient().getId(),
                projectId, before, after);
        return after;
    }

    /** A project the caller may see. Hidden projects answer "not found". */
    private Project findVisibleProject(AuthenticatedUser me, Long id) {
        if (!access.canSeeProject(me, id)) {
            throw AccessService.notFound("Project");
        }
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    /** A project whose team the caller may change. */
    private Project findManageableProject(Long id) {
        AuthenticatedUser me = access.current();
        Project project = findVisibleProject(me, id);
        if (!access.canManageProject(me, project)) {
            throw AccessService.forbidden();
        }
        return project;
    }

    private AppUser findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
    }
}