package com.sumasoft.tracking.auth;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Reads and saves login sessions.
 */
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    /** Find a session by token fingerprint, together with the employee and their role. */
    @EntityGraph(attributePaths = {"user", "user.role"})
    Optional<UserSession> findByTokenHash(String tokenHash);

    /** Log out everywhere: cancel all open sessions of one employee. */
    @Modifying
    @Query("update UserSession s set s.revokedAt = :now where s.user.id = :userId and s.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    /** Cancel all open sessions of one employee except one (used after changing the password). */
    @Modifying
    @Query("update UserSession s set s.revokedAt = :now "
            + "where s.user.id = :userId and s.revokedAt is null and s.id <> :keepId")
    int revokeOthersForUser(@Param("userId") Long userId, @Param("keepId") Long keepId, @Param("now") Instant now);
}