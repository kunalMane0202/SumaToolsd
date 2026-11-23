package com.sumasoft.tracking.project;

import com.sumasoft.tracking.client.Client;
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
import java.time.LocalDate;
import java.util.Locale;

/**
 * One row of the "projects" table (created by Flyway file V1).
 * Every project belongs to exactly one client and can never be moved to another client.
 */
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false, updatable = false)
    private Client client;

    @Column(name = "project_name", nullable = false, length = 150)
    private String projectName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "supports_l1", nullable = false)
    private boolean supportsL1;

    @Column(name = "supports_l2", nullable = false)
    private boolean supportsL2;

    @Column(name = "supports_l3", nullable = false)
    private boolean supportsL3;

    @Column(name = "support_start_date")
    private LocalDate supportStartDate;

    @Column(name = "support_end_date")
    private LocalDate supportEndDate;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = "Asia/Kolkata";

    @Column(name = "external_ticket_system", length = 50)
    private String externalTicketSystem;

    /** ACTIVE, INACTIVE or CLOSED (the database CHECK rule in V1 allows only these). */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /** Needed by JPA. Not for normal use. */
    protected Project() {
    }

    /** Make a new project under a client, from checked input. New projects start as ACTIVE. */
    public static Project create(Client client, ProjectRequest request) {
        Project project = new Project();
        project.client = client;
        project.applyDetails(request);
        project.status = "ACTIVE";
        return project;
    }

    /** Copy the editable fields from checked input. */
    public void applyDetails(ProjectRequest request) {
        this.projectName = clean(request.projectName());
        this.description = clean(request.description());
        this.supportsL1 = Boolean.TRUE.equals(request.supportsL1());
        this.supportsL2 = Boolean.TRUE.equals(request.supportsL2());
        this.supportsL3 = Boolean.TRUE.equals(request.supportsL3());
        this.supportStartDate = request.supportStartDate();
        this.supportEndDate = request.supportEndDate();
        String zone = clean(request.timeZone());
        this.timeZone = zone == null ? "Asia/Kolkata" : zone;
        String system = clean(request.externalTicketSystem());
        this.externalTicketSystem = system == null ? null : system.toUpperCase(Locale.ROOT);
    }

    /** None of these delete anything: tickets and history are always kept (FR-028, FR-029). */
    public void activate() {
        this.status = "ACTIVE";
    }

    public void deactivate() {
        this.status = "INACTIVE";
    }

    public void close() {
        this.status = "CLOSED";
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
    public Client getClient() { return client; }
    public String getProjectName() { return projectName; }
    public String getDescription() { return description; }
    public boolean isSupportsL1() { return supportsL1; }
    public boolean isSupportsL2() { return supportsL2; }
    public boolean isSupportsL3() { return supportsL3; }
    public LocalDate getSupportStartDate() { return supportStartDate; }
    public LocalDate getSupportEndDate() { return supportEndDate; }
    public String getTimeZone() { return timeZone; }
    public String getExternalTicketSystem() { return externalTicketSystem; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Integer getVersion() { return version; }
}