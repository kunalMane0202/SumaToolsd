package com.sumasoft.tracking.client;

import com.sumasoft.tracking.audit.AuditService;
import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/**
 * The business rules for clients live here (not in the controller).
 * Permissions (Step 25):
 *   see    : Super Admin, Management Viewer = all; Client Admin / PM / TL = their clients; L1-L3 = none
 *   create : Super Admin
 *   edit   : Super Admin, Client Admin of that client
 *   activate / deactivate : Super Admin
 * Audit (Step 26): every create / edit / activate / deactivate is recorded with before and after values.
 */
@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final AccessService access;
    private final AuditService audit;

    public ClientService(ClientRepository clientRepository, AccessService access, AuditService audit) {
        this.clientRepository = clientRepository;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> listClients() {
        AuthenticatedUser me = access.current();
        if (access.seesEverything(me)) {
            return clientRepository.findAll(Sort.by("id")).stream().map(ClientResponse::from).toList();
        }
        if (access.isSupportUser(me)) {
            throw AccessService.forbidden();
        }
        Set<Long> ids = access.visibleClientIds(me);
        if (ids.isEmpty()) {
            return List.of();
        }
        return clientRepository.findByIdInOrderByIdAsc(ids).stream().map(ClientResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse getClient(Long id) {
        AuthenticatedUser me = access.current();
        if (!access.canSeeClient(me, id)) {
            throw AccessService.notFound("Client");
        }
        return ClientResponse.from(findClient(id));
    }

    @Transactional
    public ClientResponse createClient(ClientRequest request) {
        access.requireSuperAdmin(access.current());
        String name = request.clientName().trim();
        if (clientRepository.existsByClientNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A client with this name already exists");
        }
        Client saved = clientRepository.saveAndFlush(Client.create(request));
        ClientResponse after = ClientResponse.from(saved);
        audit.record("CLIENT_CREATED", "CLIENT", saved.getId(), saved.getId(), null, null, after);
        return after;
    }

    @Transactional
    public ClientResponse updateClient(Long id, ClientRequest request) {
        AuthenticatedUser me = access.current();
        if (!access.canSeeClient(me, id)) {
            throw AccessService.notFound("Client");
        }
        if (!access.isSuperAdmin(me) && !access.isClientAdminOf(me, id)) {
            throw AccessService.forbidden();
        }
        Client client = findClient(id);
        String name = request.clientName().trim();
        if (clientRepository.existsByClientNameIgnoreCaseAndIdNot(name, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another client already uses this name");
        }
        ClientResponse before = ClientResponse.from(client);
        client.applyDetails(request);
        ClientResponse after = ClientResponse.from(clientRepository.saveAndFlush(client));
        audit.record("CLIENT_UPDATED", "CLIENT", id, id, null, before, after);
        return after;
    }

    @Transactional
    public ClientResponse activateClient(Long id) {
        access.requireSuperAdmin(access.current());
        Client client = findClient(id);
        ClientResponse before = ClientResponse.from(client);
        client.activate();
        ClientResponse after = ClientResponse.from(clientRepository.saveAndFlush(client));
        audit.record("CLIENT_ACTIVATED", "CLIENT", id, id, null, before, after);
        return after;
    }

    @Transactional
    public ClientResponse deactivateClient(Long id) {
        access.requireSuperAdmin(access.current());
        Client client = findClient(id);
        ClientResponse before = ClientResponse.from(client);
        client.deactivate();
        ClientResponse after = ClientResponse.from(clientRepository.saveAndFlush(client));
        audit.record("CLIENT_DEACTIVATED", "CLIENT", id, id, null, before, after);
        return after;
    }

    private Client findClient(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    }
}