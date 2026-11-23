package com.sumasoft.tracking.ticket;

import com.sumasoft.tracking.ticket.TicketChangeDtos.AssignRequest;
import com.sumasoft.tracking.ticket.TicketChangeDtos.StatusChangeRequest;
import com.sumasoft.tracking.ticket.TicketChangeDtos.TicketUpdateRequest;
import com.sumasoft.tracking.ticket.TicketDtos.TicketResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web addresses (API) for working on a ticket.
 *   PUT  /api/tickets/{id}           -> edit (subject, category, priority, ...)
 *   POST /api/tickets/{id}/status    -> change status     {"statusCode":"IN_PROGRESS","note":"..."}
 *   POST /api/tickets/{id}/assign    -> assign / reassign {"assigneeUserId":4,"note":"..."}
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketActionController {

    private final TicketActionService actions;

    public TicketActionController(TicketActionService actions) {
        this.actions = actions;
    }

    @PutMapping("/{id}")
    public TicketResponse update(@PathVariable Long id, @Valid @RequestBody TicketUpdateRequest request) {
        return actions.updateTicket(id, request);
    }

    @PostMapping("/{id}/status")
    public TicketResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request) {
        return actions.changeStatus(id, request);
    }

    @PostMapping("/{id}/assign")
    public TicketResponse assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request) {
        return actions.assign(id, request);
    }
}