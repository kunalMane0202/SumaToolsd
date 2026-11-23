package com.sumasoft.tracking.client;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Reads and saves clients in the database.
 * Spring writes the actual database code for us.
 */
public interface ClientRepository extends JpaRepository<Client, Long> {

    /** Only these clients (used when a person may see just some clients). */
    List<Client> findByIdInOrderByIdAsc(Collection<Long> ids);

    /** Is this name already used? ("Hero" and "hero" count as the same.) */
    boolean existsByClientNameIgnoreCase(String clientName);

    /** Is this name used by ANOTHER client (not the one being edited)? */
    boolean existsByClientNameIgnoreCaseAndIdNot(String clientName, Long id);
}