package com.sumasoft.tracking.ticket;

import com.sumasoft.tracking.audit.AuditService;
import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import com.sumasoft.tracking.project.Project;
import com.sumasoft.tracking.project.ProjectRepository;
import com.sumasoft.tracking.team.ProjectAssignment;
import com.sumasoft.tracking.team.ProjectAssignmentRepository;
import com.sumasoft.tracking.ticket.TicketDtos.PriorityResponse;
import com.sumasoft.tracking.ticket.TicketDtos.StatusResponse;
import com.sumasoft.tracking.ticket.TicketDtos.TicketCreateRequest;
import com.sumasoft.tracking.ticket.TicketDtos.TicketFilter;
import com.sumasoft.tracking.ticket.TicketDtos.TicketHistoryResponse;
import com.sumasoft.tracking.ticket.TicketDtos.TicketPage;
import com.sumasoft.tracking.ticket.TicketDtos.TicketResponse;
import com.sumasoft.tracking.user.AppUser;
import com.sumasoft.tracking.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Centralised tickets: list / search / filter, one ticket, history, and creating a ticket by hand.
 *
 * Permissions:
 *   see    : Super Admin, Management Viewer = all tickets; everyone else = tickets of projects they are on
 *   create : anyone who may see the project, except the read-only Management Viewer
 *            (the project must be ACTIVE; an assignee must be an active member of the project team)
 * Every creation is written to the ticket history and to the audit history.
 * Date filters (createdFrom / createdTo) are whole days in India time (Asia/Kolkata).
 */
@Service
public class TicketService {

    static final ZoneId FILTER_ZONE = ZoneId.of("Asia/Kolkata");
    private static final int MAX_PAGE_SIZE = 200;

    private final TicketRepository ticketRepository;
    private final TicketHistoryRepository historyRepository;
    private final TicketStatusRepository statusRepository;
    private final TicketPriorityRepository priorityRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final AccessService access;
    private final AuditService audit;

    public TicketService(TicketRepository ticketRepository, TicketHistoryRepository historyRepository,
                         TicketStatusRepository statusRepository, TicketPriorityRepository priorityRepository,
                         ProjectRepository projectRepository, ProjectAssignmentRepository assignmentRepository,
                         UserRepository userRepository, AccessService access, AuditService audit) {
        this.ticketRepository = ticketRepository;
        this.historyRepository = historyRepository;
        this.statusRepository = statusRepository;
        this.priorityRepository = priorityRepository;
        this.projectRepository = projectRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.access = access;
        this.audit = audit;
    }

    // ---------------------------------------------------------------- lookups

    @Transactional(readOnly = true)
    public List<StatusResponse> listStatuses() {
        return statusRepository.findByActiveTrueOrderBySortOrderAsc().stream().map(StatusResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PriorityResponse> listPriorities() {
        return priorityRepository.findAllByOrderBySortOrderAsc().stream().map(PriorityResponse::from).toList();
    }

    // ---------------------------------------------------------------- list / search / filter

    @Transactional(readOnly = true)
    public TicketPage search(TicketFilter filter, int page, int size) {
        AuthenticatedUser me = access.current();
        Set<Long> allowedProjects = null;                 // null = no limit (sees everything)
        if (!access.seesEverything(me)) {
            allowedProjects = access.visibleProjectIds(me);
            if (allowedProjects.isEmpty()) {
                return new TicketPage(List.of(), Math.max(page, 0), size, 0, 0);
            }
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        PageRequest pageRequest = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAtSource").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<Ticket> result = ticketRepository.findAll(buildSearch(filter, allowedProjects), pageRequest);
        Instant now = Instant.now();
        List<TicketResponse> items = result.getContent().stream().map(t -> TicketResponse.from(t, now)).toList();
        return new TicketPage(items, safePage, safeSize, result.getTotalElements(), result.getTotalPages());
    }

    /** Turns the chosen filters into a database search. */
    private static Specification<Ticket> buildSearch(TicketFilter f, Set<Long> allowedProjects) {
        return (root, query, cb) -> {
            List<Predicate> rules = new ArrayList<>();
            if (allowedProjects != null) {
                rules.add(root.get("project").get("id").in(allowedProjects));
            }
            if (f.clientId() != null) {
                rules.add(cb.equal(root.get("client").get("id"), f.clientId()));
            }
            if (f.projectId() != null) {
                rules.add(cb.equal(root.get("project").get("id"), f.projectId()));
            }
            if (notBlank(f.status())) {
                rules.add(cb.equal(root.get("status").get("code"), f.status().trim().toUpperCase(Locale.ROOT)));
            }
            if (notBlank(f.statusGroup())) {
                rules.add(cb.equal(root.get("status").get("statusGroup"), f.statusGroup().trim().toUpperCase(Locale.ROOT)));
            }
            if (notBlank(f.priority())) {
                rules.add(cb.equal(root.get("priority").get("code"), f.priority().trim().toUpperCase(Locale.ROOT)));
            }
            if (notBlank(f.category())) {
                rules.add(cb.equal(cb.lower(root.<String>get("category")), f.category().trim().toLowerCase(Locale.ROOT)));
            }
            if (f.assigneeUserId() != null) {
                rules.add(cb.equal(root.get("assignee").get("id"), f.assigneeUserId()));
            }
            if (f.escalated() != null) {
                rules.add(cb.equal(root.get("escalated"), f.escalated()));
            }
            if (f.createdFrom() != null) {
                Instant from = f.createdFrom().atStartOfDay(FILTER_ZONE).toInstant();
                rules.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAtSource"), from));
            }
            if (f.createdTo() != null) {
                Instant toExclusive = f.createdTo().plusDays(1).atStartOfDay(FILTER_ZONE).toInstant();
                rules.add(cb.lessThan(root.<Instant>get("createdAtSource"), toExclusive));
            }
            if (notBlank(f.q())) {
                String q = f.q().trim();
                List<Predicate> any = new ArrayList<>();
                any.add(cb.equal(root.get("externalTicketId"), q));
                String like = "%" + q.toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                any.add(cb.like(cb.lower(root.<String>get("subject")), like, '\\'));
                Long internalId = parseInternalId(q);
                if (internalId != null) {
                    any.add(cb.equal(root.get("id"), internalId));
                }
                rules.add(cb.or(any.toArray(new Predicate[0])));
            }
            return cb.and(rules.toArray(new Predicate[0]));
        };
    }

    /** "TK-000012" or "12" -> 12. Anything else -> null. */
    private static Long parseInternalId(String q) {
        String digits = q.toUpperCase(Locale.ROOT).startsWith("TK-") ? q.substring(3) : q;
        if (!digits.matches("^[0-9]{1,15}$")) {
            return null;
        }
        return Long.parseLong(digits);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    // ---------------------------------------------------------------- one ticket

    @Transactional(readOnly = true)
    public TicketResponse getTicket(Long id) {
        return TicketResponse.from(findVisibleTicket(access.current(), id), Instant.now());
    }

    @Transactional(readOnly = true)
    public List<TicketHistoryResponse> getHistory(Long id) {
        findVisibleTicket(access.current(), id);
        return historyRepository.findByTicketIdOrderByIdAsc(id).stream().map(TicketHistoryResponse::from).toList();
    }

    // ---------------------------------------------------------------- create

    @Transactional
    public TicketResponse createTicket(TicketCreateRequest request) {
        AuthenticatedUser me = access.current();
        Project project = projectRepository.findById(request.projectId())
                .filter(p -> access.canSeeProject(me, p.getId()))
                .orElseThrow(() -> AccessService.notFound("Project"));
        if (access.seesEverything(me) && !access.isSuperAdmin(me)) {
            throw AccessService.forbidden();               // Management Viewer is read-only
        }
        if (!"ACTIVE".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tickets can only be created on ACTIVE projects. This project is " + project.getStatus());
        }
        TicketStatus open = statusRepository.findByCode("OPEN")
                .orElseThrow(() -> new IllegalStateException("Status OPEN is missing"));
        TicketPriority priority = findPriority(request.priorityCode());
        AppUser assignee = findAssignee(project, request.assigneeUserId());

        Instant now = Instant.now();
        Ticket saved = ticketRepository.saveAndFlush(
                Ticket.createManual(project, request, open, priority, assignee, now));
        AppUser actor = userRepository.getReferenceById(me.userId());
        historyRepository.save(TicketHistory.of(saved, "CREATED", null, null, open.getCode(),
                actor, "USER", "Created by hand", now));
        if (assignee != null) {
            historyRepository.save(TicketHistory.of(saved, "ASSIGNED", "assignee", null, assignee.getFullName(),
                    actor, "USER", null, now));
        }
        TicketResponse after = TicketResponse.from(saved, now);
        audit.record("TICKET_CREATED", "TICKET", saved.getId(), project.getClient().getId(), project.getId(),
                null, after);
        return after;
    }

    // ---------------------------------------------------------------- helpers

    /** A ticket the caller may see. Hidden tickets answer "not found". */
    Ticket findVisibleTicket(AuthenticatedUser me, Long id) {
        Ticket ticket = ticketRepository.findWithDetailsById(id)
                .orElseThrow(() -> AccessService.notFound("Ticket"));
        if (!access.canSeeProject(me, ticket.getProject().getId())) {
            throw AccessService.notFound("Ticket");
        }
        return ticket;
    }

    TicketPriority findPriority(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return priorityRepository.findById(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Priority must be CRITICAL, HIGH, MEDIUM or LOW"));
    }

    /** The assignee must be an ACTIVE employee who is an ACTIVE member of this project's team. */
    AppUser findAssignee(Project project, Long userId) {
        if (userId == null) {
            return null;
        }
        ProjectAssignment membership = assignmentRepository.findByProjectIdAndUserId(project.getId(), userId)
                .filter(ProjectAssignment::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The assignee must be an active member of this project's team"));
        AppUser user = membership.getUser();
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The assignee is not an active employee");
        }
        return user;
    }
}