package com.sumasoft.tracking.team;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Reads and saves project team memberships.
 */
public interface ProjectAssignmentRepository extends JpaRepository<ProjectAssignment, Long> {

    @EntityGraph(attributePaths = {"user", "user.role", "project", "project.client"})
    List<ProjectAssignment> findByProjectIdOrderByIdAsc(Long projectId);

    @EntityGraph(attributePaths = {"user", "user.role", "project", "project.client"})
    List<ProjectAssignment> findByProjectIdAndActiveTrueOrderByIdAsc(Long projectId);

    @EntityGraph(attributePaths = {"user", "user.role", "project", "project.client"})
    List<ProjectAssignment> findByUserIdOrderByIdAsc(Long userId);

    Optional<ProjectAssignment> findByProjectIdAndUserId(Long projectId, Long userId);

    /** Is there already an active person with this project role (other than this employee)? */
    boolean existsByProjectIdAndProjectRoleAndActiveTrueAndUserIdNot(Long projectId, String projectRole, Long userId);

    /** Is this person active on this project with this project role? (used for permissions) */
    boolean existsByProjectIdAndUserIdAndProjectRoleAndActiveTrue(Long projectId, Long userId, String projectRole);

    /** The projects this person is actively on (used for permissions). */
    @Query("select a.project.id from ProjectAssignment a where a.user.id = :userId and a.active = true")
    List<Long> findActiveProjectIdsByUserId(@Param("userId") Long userId);

    /** The people actively on any of these projects (used for permissions). */
    @Query("select distinct a.user.id from ProjectAssignment a where a.project.id in :projectIds and a.active = true")
    List<Long> findActiveUserIdsByProjectIds(@Param("projectIds") Collection<Long> projectIds);
}