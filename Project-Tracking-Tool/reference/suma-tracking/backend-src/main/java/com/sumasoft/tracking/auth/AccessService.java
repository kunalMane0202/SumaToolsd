package com.sumasoft.tracking.auth;

import com.sumasoft.tracking.project.Project;
import com.sumasoft.tracking.project.ProjectRepository;
import com.sumasoft.tracking.team.ProjectAssignmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Set;

/**
 * ONE place for "who may see / change what" (the approved permission rules).
 *
 *  - SUPER_ADMIN        : sees and changes everything.
 *  - MANAGEMENT_VIEWER  : sees everything, changes nothing (read-only).
 *  - CLIENT_ADMIN       : sees the clients and projects they are assigned to; may edit those clients,
 *                         and create / edit / activate / deactivate / close projects of those clients.
 *  - PROJECT_MANAGER, TEAM_LEADER : see their assigned projects (and those clients).
 *  - Anyone who is PROJECT_MANAGER *on a project* may edit that project and its team.
 *  - L1 / L2 / L3       : see only their assigned projects. No client or employee lists.
 *
 * Records a person may not see are answered with 404 ("not found"), so their existence is not revealed.
 * Records a person may see but not change are answered with 403 ("no permission").
 */
@Service
public class AccessService {

    private final ProjectAssignmentRepository assignmentRepository;
    private final ProjectRepository projectRepository;

    public AccessService(ProjectAssignmentRepository assignmentRepository, ProjectRepository projectRepository) {
        this.assignmentRepository = assignmentRepository;
        this.projectRepository = projectRepository;
    }

    /** The logged-in person making this request. */
    public AuthenticatedUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in");
    }

    // ---------------------------------------------------------------- simple role questions

    public boolean isSuperAdmin(AuthenticatedUser u) {
        return "SUPER_ADMIN".equals(u.roleCode());
    }

    public boolean isClientAdmin(AuthenticatedUser u) {
        return "CLIENT_ADMIN".equals(u.roleCode());
    }

    /** Super Admin and Management Viewer can SEE everything. */
    public boolean seesEverything(AuthenticatedUser u) {
        return isSuperAdmin(u) || "MANAGEMENT_VIEWER".equals(u.roleCode());
    }

    public boolean isSupportUser(AuthenticatedUser u) {
        return "SUPPORT_USER".equals(u.userGroup());
    }

    // ---------------------------------------------------------------- what a person can see

    /** Projects where this person is on the team (active). */
    public Set<Long> visibleProjectIds(AuthenticatedUser u) {
        return new HashSet<>(assignmentRepository.findActiveProjectIdsByUserId(u.userId()));
    }

    /** Clients of the projects this person is on. */
    public Set<Long> visibleClientIds(AuthenticatedUser u) {
        Set<Long> projectIds = visibleProjectIds(u);
        if (projectIds.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(projectRepository.findClientIdsByIdIn(projectIds));
    }

    public boolean canSeeProject(AuthenticatedUser u, Long projectId) {
        return seesEverything(u) || visibleProjectIds(u).contains(projectId);
    }

    public boolean canSeeClient(AuthenticatedUser u, Long clientId) {
        if (seesEverything(u)) {
            return true;
        }
        return !isSupportUser(u) && visibleClientIds(u).contains(clientId);
    }

    // ---------------------------------------------------------------- what a person can change

    /** Client Admin of this client (assigned to at least one of its projects). */
    public boolean isClientAdminOf(AuthenticatedUser u, Long clientId) {
        return isClientAdmin(u) && visibleClientIds(u).contains(clientId);
    }

    /** May edit this project's settings and team. */
    public boolean canManageProject(AuthenticatedUser u, Project project) {
        if (isSuperAdmin(u)) {
            return true;
        }
        if (isClientAdminOf(u, project.getClient().getId())) {
            return true;
        }
        return assignmentRepository.existsByProjectIdAndUserIdAndProjectRoleAndActiveTrue(
                project.getId(), u.userId(), "PROJECT_MANAGER");
    }

    // ---------------------------------------------------------------- standard answers

    public void requireSuperAdmin(AuthenticatedUser u) {
        if (!isSuperAdmin(u)) {
            throw forbidden();
        }
    }

    public static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission for this action");
    }

    public static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found");
    }
}