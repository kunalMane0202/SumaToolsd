package com.sumasoft.tracking.user;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Reads and saves employees in the database.
 */
public interface UserRepository extends JpaRepository<AppUser, Long> {

    @EntityGraph(attributePaths = {"role", "reportingManager"})
    List<AppUser> findAllByOrderByIdAsc();

    /** Only these employees (used when a person may see just some employees). */
    @EntityGraph(attributePaths = {"role", "reportingManager"})
    List<AppUser> findByIdInOrderByIdAsc(Collection<Long> ids);

    /** Used by login: find the employee by email, with their role. */
    @EntityGraph(attributePaths = "role")
    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

    boolean existsByEmployeeCodeIgnoreCaseAndIdNot(String employeeCode, Long id);
}