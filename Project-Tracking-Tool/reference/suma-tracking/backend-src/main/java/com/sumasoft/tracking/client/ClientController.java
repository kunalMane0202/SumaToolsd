package com.sumasoft.tracking.client;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Web addresses (API) for clients.
 *   GET  /api/clients                  -> all clients
 *   GET  /api/clients/{id}             -> one client
 *   POST /api/clients                  -> create a client
 *   PUT  /api/clients/{id}             -> edit a client
 *   POST /api/clients/{id}/activate    -> make ACTIVE
 *   POST /api/clients/{id}/deactivate  -> make INACTIVE (nothing is deleted)
 * NOTE: no login yet. Login and permissions are added in Phase 4.
 */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    public List<ClientResponse> listClients() {
        return clientService.listClients();
    }

    @GetMapping("/{id}")
    public ClientResponse getClient(@PathVariable Long id) {
        return clientService.getClient(id);
    }

    @PostMapping
    public ResponseEntity<ClientResponse> createClient(@Valid @RequestBody ClientRequest request) {
        ClientResponse created = clientService.createClient(request);
        return ResponseEntity.created(URI.create("/api/clients/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ClientResponse updateClient(@PathVariable Long id, @Valid @RequestBody ClientRequest request) {
        return clientService.updateClient(id, request);
    }

    @PostMapping("/{id}/activate")
    public ClientResponse activateClient(@PathVariable Long id) {
        return clientService.activateClient(id);
    }

    @PostMapping("/{id}/deactivate")
    public ClientResponse deactivateClient(@PathVariable Long id) {
        return clientService.deactivateClient(id);
    }
}