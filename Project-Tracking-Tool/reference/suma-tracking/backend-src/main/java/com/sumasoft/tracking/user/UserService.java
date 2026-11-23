package com.sumasoft.tracking.user;

import com.sumasoft.tracking.audit.AuditService;
import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import com.sumasoft.tracking.team.ProjectAssignmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The business rules for employees and roles live here.
 * Permissions (Step 25):
 *   roles list : everyone who is logged in
 *   see        : Super Admin, Management Viewer = all; Client Admin / PM / TL = people on their projects;
 *                L1-L3 = only themselves (GET /api/users/{ownId} or /api/auth/me)
 *   create / edit / activate / deactivate : Super Admin only
 * Audit (Step 26): every create / edit / activate / deactivate is recorded (never any password data).
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ProjectAssignmentRepository assignmentRepository;
    private final AccessService access;
    private final AuditService audit;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                       ProjectAssignmentRepository assignmentRepository, AccessService access,
                       AuditService audit) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAllByOrderBySortOrderAsc().stream().map(RoleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        AuthenticatedUser me = access.current();
        if (access.seesEverything(me)) {
            return userRepository.findAllByOrderByIdAsc().stream().map(UserResponse::from).toList();
        }
        if (access.isSupportUser(me)) {
            throw AccessService.forbidden();
        }
        return userRepository.findByIdInOrderByIdAsc(visibleUserIds(me)).stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        AuthenticatedUser me = access.current();
        boolean allowed = access.seesEverything(me)
                || me.userId().equals(id)
                || (!access.isSupportUser(me) && visibleUserIds(me).contains(id));
        if (!allowed) {
            throw AccessService.notFound("Employee");
        }
        return UserResponse.from(findUser(id));
    }

    /** People who share at least one project with me, plus me. */
    private Set<Long> visibleUserIds(AuthenticatedUser me) {
        Set<Long> ids = new HashSet<>();
        ids.add(me.userId());
        Set<Long> projectIds = access.visibleProjectIds(me);
        if (!projectIds.isEmpty()) {
            ids.addAll(assignmentRepository.findActiveUserIdsByProjectIds(projectIds));
        }
        return ids;
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {
        access.requireSuperAdmin(access.current());
        String email = request.email().trim();
        String code = request.employeeCode().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An employee with this email already exists");
        }
        if (userRepository.existsByEmployeeCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An employee with this Employee ID already exists");
        }
        checkShift(request);
        Role role = findRole(request.roleCode());
        AppUser manager = findManager(request.reportingManagerId(), null);
        AppUser saved = userRepository.saveAndFlush(AppUser.create(request, role, manager));
        UserResponse after = UserResponse.from(saved);
        audit.record("USER_CREATED", "USER", saved.getId(), null, null, null, after);
        return after;
    }

    @Transactional
    public UserResponse updateUser(Long id, UserRequest request) {
        access.requireSuperAdmin(access.current());
        AppUser user = findUser(id);
        String email = request.email().trim();
        String code = request.employeeCode().trim();
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another employee already uses this email");
        }
        if (userRepository.existsByEmployeeCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another employee already uses this Employee ID");
        }
        checkShift(request);
        Role role = findRole(request.roleCode());
        AppUser manager = findManager(request.reportingManagerId(), id);
        UserResponse before = UserResponse.from(user);
        user.applyDetails(request, role, manager);
        UserResponse after = UserResponse.from(userRepository.saveAndFlush(user));
        audit.record("USER_UPDATED", "USER", id, null, null, before, after);
        return after;
    }

    @Transactional
    public UserResponse activateUser(Long id) {
        access.requireSuperAdmin(access.current());
        AppUser user = findUser(id);
        UserResponse before = UserResponse.from(user);
        user.activate();
        UserResponse after = UserResponse.from(userRepository.saveAndFlush(user));
        audit.record("USER_ACTIVATED", "USER", id, null, null, before, after);
        return after;
    }

    @Transactional
    public UserResponse deactivateUser(Long id) {
        access.requireSuperAdmin(access.current());
        AppUser user = findUser(id);
        UserResponse before = UserResponse.from(user);
        user.deactivate();
        UserResponse after = UserResponse.from(userRepository.saveAndFlush(user));
        audit.record("USER_DEACTIVATED", "USER", id, null, null, before, after);
        return after;
    }

    private AppUser findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
    }

    private Role findRole(String roleCode) {
        String code = roleCode.trim().toUpperCase(Locale.ROOT);
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Role does not exist. See GET /api/roles for the list"));
    }

    /**
     * Checks the reporting manager: must exist, be ACTIVE, not be the employee,
     * and must not create a loop (A reports to B, B reports to A).
     */
    private AppUser findManager(Long managerId, Long employeeId) {
        if (managerId == null) {
            return null;
        }
        if (managerId.equals(employeeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An employee cannot report to themselves");
        }
        AppUser manager = userRepository.findById(managerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reporting manager does not exist"));
        if (!"ACTIVE".equals(manager.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reporting manager is not active");
        }
        if (employeeId != null) {
            AppUser step = manager.getReportingManager();
            int guard = 0;
            while (step != null && guard < 100) {
                if (employeeId.equals(step.getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "This would create a reporting loop (A reports to B and B reports to A)");
                }
                step = step.getReportingManager();
                guard++;
            }
        }
        return manager;
    }

    private void checkShift(UserRequest request) {
        if ((request.shiftStart() == null) != (request.shiftEnd() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Give both shift start and shift end, or neither");
        }
    }
}