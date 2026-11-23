package com.sumasoft.tracking.team;

import com.sumasoft.tracking.project.Project;
import com.sumasoft.tracking.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * One row of the "project_assignments" table (created by Flyway file V3):
 * "this employee works on this project, as this project role, at this support level".
 * Removing someone sets active = false, so the history is kept.
 */
@Entity
@Table(name = "project_assignments")
public class ProjectAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    /** PROJECT_MANAGER, TEAM_LEADER or MEMBER (checked by the database). */
    @Column(name = "project_role", nullable = false, length = 30)
    private String projectRole;

    /** L1, L2 or L3 (or empty). */
    @Column(name = "support_level", length = 2)
    private String supportLevel;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /** Needed by JPA. Not for normal use. */
    protected ProjectAssignment() {
    }

    public static ProjectAssignment create(Project project, AppUser user, String projectRole, String supportLevel) {
        ProjectAssignment a = new ProjectAssignment();
        a.project = project;
        a.user = user;
        a.change(projectRole, supportLevel);
        return a;
    }

    /** Set (or re-set) the job on this project. Also brings back someone who was removed. */
    public void change(String projectRole, String supportLevel) {
        this.projectRole = projectRole;
        this.supportLevel = supportLevel;
        this.active = true;
    }

    /** Remove from the project, but keep the record (history). */
    public void remove() {
        this.active = false;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public AppUser getUser() { return user; }
    public String getProjectRole() { return projectRole; }
    public String getSupportLevel() { return supportLevel; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Integer getVersion() { return version; }
}