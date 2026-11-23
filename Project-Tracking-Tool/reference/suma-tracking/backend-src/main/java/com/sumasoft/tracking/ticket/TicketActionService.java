package com.sumasoft.tracking.ticket;

import com.sumasoft.tracking.audit.AuditService;
import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import com.sumasoft.tracking.project.Project;
import com.sumasoft.tracking.team.ProjectAssignment;
import com.sumasoft.tracking.team.ProjectAssignmentRepository;
import com.sumasoft.tracking.ticket.TicketChangeDtos.AssignRequest;
import com.sumasoft.tracking.ticket.TicketChangeDtos.StatusChangeRequest;
import com.sumasoft.tracking.ticket.TicketChangeDtos.TicketUpdateRequest;
import com.sumasoft.tracking.ticket.TicketDtos.TicketResponse;
import com.sumasoft.tracking.user.AppUser;
import com.sumasoft.tracking.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Working on a ticket: edit, change status, assign / reassign.
 *
 * Permissions:
 *   "leads" of a ticket = Super Admin, Client Admin of its client, Project Manager / Team Leader of its project
 *   edit / change status : leads, or the employee the ticket is assigned to
 *   assign / reassign    : leads only (only to active members of the project team)
 *   reopen a Closed / Merged ticket : leads only
 *   Management Viewer    : nothing (read-only)
 * Every change is written to the ticket history and to the audit history.
 */
@Service
public class TicketActionService {

    private final TicketRepository ticketRepository;
    private final TicketHistoryRepository historyRepository;
    private final TicketStatusRepository statusRepository;
    private final TicketPriorityRepository priorityRepository;
    private final ProjectAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final AccessService access;
    private final AuditService audit;

    public TicketActionService(TicketRepository ticketRepository, TicketHistoryRepository historyRepository,
                               TicketStatusRepository statusRepository, TicketPriorityRepository priorityRepository,
                               ProjectAssignmentRepository assignmentRepository, UserRepository userRepository,
                               AccessService access, AuditService audit) {
        this.ticketRepository = ticketRepository;
        this.historyRepository = historyRepository;
        this.statusRepository = statusRepository;
        this.priorityRepository = priorityRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.access = access;
        this.audit = audit;
    }

    @Transactional
    public TicketResponse updateTicket(Long id, TicketUpdateRequest request) {
        AuthenticatedUser me = access.current();
        Ticket ticket = findVisibleTicket(me, id);
        if (!isLead(me, ticket) && !isAssignedTo(me, ticket)) {
            throw AccessService.forbidden();
        }
        TicketPriority priority = findPriority(request.priorityCode());
        Instant now = Instant.now();
        TicketResponse before = TicketResponse.from(ticket, now);
        List<Ticket.FieldChange> changes = ticket.applyEdit(request, priority, now);
        if (changes.isEmpty()) {
            return before;
        }
        ticketRepository.saveAndFlush(ticket);
        AppUser actor = userRepository.getReferenceById(me.userId());
        for (Ticket.FieldChange c : changes) {
            historyRepository.save(TicketHistory.of(ticket, "UPDATED", c.field(), c.oldValue(), c.newValue(),
                    actor, "USER", null, now));
        }
        TicketResponse after = TicketResponse.from(ticket, now);
        audit.record("TICKET_UPDATED", "TICKET", id, after.clientId(), after.projectId(), before, after);
        return after;
    }

    @Transactional
    public TicketResponse changeStatus(Long id, StatusChangeRequest request) {
        AuthenticatedUser me = access.current();
        Ticket ticket = findVisibleTicket(me, id);
        boolean lead = isLead(me, ticket);
        if (!lead && !isAssignedTo(me, ticket)) {
            throw AccessService.forbidden();
        }
        TicketStatus newStatus = statusRepository.findByCode(request.statusCode().trim().toUpperCase(Locale.ROOT))
                .filter(TicketStatus::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown status. See GET /api/ticket-statuses"));
        if (newStatus.getId().equals(ticket.getStatus().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The ticket already has this status");
        }
        if (ticket.getStatus().isFinalStatus() && !lead) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only a Team Leader, Project Manager or admin can reopen a " + ticket.getStatus().getName() + " ticket");
        }
        Instant now = Instant.now();
        TicketResponse before = TicketResponse.from(ticket, now);
        String oldCode = ticket.getStatus().getCode();
        AppUser actor = userRepository.getReferenceById(me.userId());
        ticket.changeStatus(newStatus, actor, now);
        ticketRepository.saveAndFlush(ticket);
        historyRepository.save(TicketHistory.of(ticket, "STATUS_CHANGED", "status", oldCode, newStatus.getCode(),
                actor, "USER", Ticket.clean(request.note()), now));
        TicketResponse after = TicketResponse.from(ticket, now);
        audit.record("TICKET_STATUS_CHANGED", "TICKET", id, after.clientId(), after.projectId(), before, after);
        return after;
    }

    @Transactional
    public TicketResponse assign(Long id, AssignRequest request) {
        AuthenticatedUser me = access.current();
        Ticket ticket = findVisibleTicket(me, id);
        if (!isLead(me, ticket)) {
            throw AccessService.forbidden();
        }
        if (ticket.getStatus().isDone()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A " + ticket.getStatus().getName() + " ticket cannot be reassigned. Reopen it first");
        }
        AppUser newAssignee = findAssignee(ticket.getProject(), request.assigneeUserId());
        Long oldId = ticket.getAssignee() == null ? null : ticket.getAssignee().getId();
        Long newId = newAssignee == null ? null : newAssignee.getId();
        if (Objects.equals(oldId, newId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The ticket is already assigned this way");
        }
        Instant now = Instant.now();
        TicketResponse before = TicketResponse.from(ticket, now);
        String oldName = before.assigneeName();
        ticket.assignTo(newAssignee, now);
        ticketRepository.saveAndFlush(ticket);
        AppUser actor = userRepository.getReferenceById(me.userId());
        historyRepository.save(TicketHistory.of(ticket, "ASSIGNED", "assignee", oldName,
                newAssignee == null ? null : newAssignee.getFullName(), actor, "USER", Ticket.clean(request.note()), now));
        TicketResponse after = TicketResponse.from(ticket, now);
        audit.record("TICKET_ASSIGNED", "TICKET", id, after.clientId(), after.projectId(), before, after);
        return after;
    }

    /** Super Admin, Client Admin of the client, or Project Manager / Team Leader on the project. */
    private boolean isLead(AuthenticatedUser me, Ticket ticket) {
        if (access.isSuperAdmin(me) || access.isClientAdminOf(me, ticket.getClient().getId())) {
            return true;
        }
        return assignmentRepository.findByProjectIdAndUserId(ticket.getProject().getId(), me.userId())
                .filter(ProjectAssignment::isActive)
                .map(a -> "PROJECT_MANAGER".equals(a.getProjectRole()) || "TEAM_LEADER".equals(a.getProjectRole()))
                .orElse(false);
    }

    private static boolean isAssignedTo(AuthenticatedUser me, Ticket ticket) {
        return ticket.getAssignee() != null && me.userId().equals(ticket.getAssignee().getId());
    }

    /** A ticket the caller may see. Hidden tickets answer "not found". */
    private Ticket findVisibleTicket(AuthenticatedUser me, Long id) {
        Ticket ticket = ticketRepository.findWithDetailsById(id)
                .orElseThrow(() -> AccessService.notFound("Ticket"));
        if (!access.canSeeProject(me, ticket.getProject().getId())) {
            throw AccessService.notFound("Ticket");
        }
        return ticket;
    }

    private TicketPriority findPriority(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return priorityRepository.findById(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Priority must be CRITICAL, HIGH, MEDIUM or LOW"));
    }

    /** The assignee must be an ACTIVE employee who is an ACTIVE member of this project's team (null = nobody). */
    private AppUser findAssignee(Project project, Long userId) {
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