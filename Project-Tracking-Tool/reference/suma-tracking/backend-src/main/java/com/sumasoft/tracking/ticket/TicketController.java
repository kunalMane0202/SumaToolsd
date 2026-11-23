package com.sumasoft.tracking.ticket;

import com.sumasoft.tracking.ticket.TicketDtos.PriorityResponse;
import com.sumasoft.tracking.ticket.TicketDtos.StatusResponse;
import com.sumasoft.tracking.ticket.TicketDtos.TicketCreateRequest;
import com.sumasoft.tracking.ticket.TicketDtos.TicketFilter;
import com.sumasoft.tracking.ticket.TicketDtos.TicketHistoryResponse;
import com.sumasoft.tracking.ticket.TicketDtos.TicketPage;
import com.sumasoft.tracking.ticket.TicketDtos.TicketResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * Web addresses (API) for tickets.
 *   GET  /api/ticket-statuses               -> the statuses (Open ... Merged)
 *   GET  /api/ticket-priorities             -> Critical, High, Medium, Low
 *   GET  /api/tickets                       -> list (newest first), with optional filters:
 *        q (ticket number / TK-000001 / words in the subject), clientId, projectId,
 *        status, statusGroup, priority, category, assigneeUserId, escalated,
 *        createdFrom, createdTo (yyyy-mm-dd, India time), page (from 0), size (max 200)
 *   GET  /api/tickets/{id}                  -> one ticket
 *   GET  /api/tickets/{id}/history          -> its history
 *   POST /api/tickets                       -> create a ticket by hand
 */
@RestController
@RequestMapping("/api")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/ticket-statuses")
    public List<StatusResponse> statuses() {
        return ticketService.listStatuses();
    }

    @GetMapping("/ticket-priorities")
    public List<PriorityResponse> priorities() {
        return ticketService.listPriorities();
    }

    @GetMapping("/tickets")
    public TicketPage search(@RequestParam(required = false) String q,
                             @RequestParam(required = false) Long clientId,
                             @RequestParam(required = false) Long projectId,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) String statusGroup,
                             @RequestParam(required = false) String priority,
                             @RequestParam(required = false) String category,
                             @RequestParam(required = false) Long assigneeUserId,
                             @RequestParam(required = false) Boolean escalated,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "50") int size) {
        TicketFilter filter = new TicketFilter(q, clientId, projectId, status, statusGroup, priority, category,
                assigneeUserId, escalated, createdFrom, createdTo);
        return ticketService.search(filter, page, size);
    }

    @GetMapping("/tickets/{id}")
    public TicketResponse getTicket(@PathVariable Long id) {
        return ticketService.getTicket(id);
    }

    @GetMapping("/tickets/{id}/history")
    public List<TicketHistoryResponse> history(@PathVariable Long id) {
        return ticketService.getHistory(id);
    }

    @PostMapping("/tickets")
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketCreateRequest request) {
        TicketResponse created = ticketService.createTicket(request);
        return ResponseEntity.created(URI.create("/api/tickets/" + created.id())).body(created);
    }
}