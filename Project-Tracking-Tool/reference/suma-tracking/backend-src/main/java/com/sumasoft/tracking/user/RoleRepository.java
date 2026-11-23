package com.sumasoft.tracking.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Reads roles from the database.
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    List<Role> findAllByOrderBySortOrderAsc();
}