package com.sumasoft.tracking.auth;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Saves login history rows. (Rows are only ever added.)
 */
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
}