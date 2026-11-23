package com.sumasoft.tracking.ticket;

import com.sumasoft.tracking.client.Client;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One row of the "tickets" table (Flyway V6). Holds the FRD fields and all 23 original Excel columns.
 * The client and project of a ticket never change (the database also guarantees they match).
 * (The JSON column extra_fields is not used by the program yet.)
 * Status / assignee / field changes are made only through the methods below, so the rules
 * about resolved / closed times are always applied the same way.
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false, updatable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(name = "source_system", nullable = false, length = 30, updatable = false)
    private String sourceSystem;

    @Column(name = "external_ticket_id", length = 100)
    private String externalTicketId;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "subcategory", length = 100)
    private String subcategory;

    @Column(name = "issue_type", length = 100)
    private String issueType;

    @Column(name = "topic", length = 200)
    private String topic;

    @Column(name = "plan", length = 100)
    private String plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "priority_code")
    private TicketPriority priority;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private TicketStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_user_id")
    private AppUser assignee;

    @Column(name = "assignee_name_raw", length = 150)
    private String assigneeNameRaw;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_user_id")
    private AppUser closedBy;

    @Column(name = "closed_by_name_raw", length = 150)
    private String closedByNameRaw;

    @Column(name = "requester_name", length = 200)
    private String requesterName;

    @Column(name = "requester_role", length = 100)
    private String requesterRole;

    /** How the query arrived: Email, Chat, Call, ... (Excel column "Source"). */
    @Column(name = "channel", length = 50)
    private String channel;

    @Column(name = "escalated", nullable = false)
    private boolean escalated;

    @Column(name = "satisfaction_rating", length = 50)
    private String satisfactionRating;

    @Column(name = "created_at_source", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAtSource;

    @Column(name = "updated_at_source", columnDefinition = "timestamptz")
    private Instant updatedAtSource;

    @Column(name = "first_response_at", columnDefinition = "timestamptz")
    private Instant firstResponseAt;

    @Column(name = "resolved_at", columnDefinition = "timestamptz")
    private Instant resolvedAt;

    @Column(name = "closed_at", columnDefinition = "timestamptz")
    private Instant closedAt;

    @Column(name = "sla_due_at", columnDefinition = "timestamptz")
    private Instant slaDueAt;

    @Column(name = "timeframe_raw", length = 50)
    private String timeframeRaw;

    @Column(name = "day_raw", length = 20)
    private String dayRaw;

    @Column(name = "resolution_time_raw", length = 20)
    private String resolutionTimeRaw;

    @Column(name = "import_row_number")
    private Integer importRowNumber;

    @Column(name = "import_batch_id")
    private Long importBatchId;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /** Needed by JPA. Not for normal use. */
    protected Ticket() {
    }

    /** A ticket typed in by a person in this application (source MANUAL, status Open). */
    public static Ticket createManual(Project project, TicketDtos.TicketCreateRequest request,
                                      TicketStatus openStatus, TicketPriority priority, AppUser assignee, Instant now) {
        Ticket t = new Ticket();
        t.client = project.getClient();
        t.project = project;
        t.sourceSystem = "MANUAL";
        t.subject = clean(request.subject());
        t.description = clean(request.description());
        t.category = clean(request.category());
        t.subcategory = clean(request.subcategory());
        t.issueType = clean(request.issueType());
        t.topic = clean(request.topic());
        t.plan = clean(request.plan());
        t.priority = priority;
        t.status = openStatus;
        t.assignee = assignee;
        t.requesterName = clean(request.requesterName());
        t.requesterRole = clean(request.requesterRole());
        t.channel = clean(request.channel());
        t.escalated = Boolean.TRUE.equals(request.escalated());
        t.createdAtSource = now;
        t.updatedAtSource = now;
        return t;
    }

    /**
     * A ticket that comes from an imported Excel/CSV file (Step 32b).
     * The times come from the file (already checked by the import row checker).
     */
    public static Ticket createImported(Project project, String sourceSystem, ImportedTicketValues v,
                                        TicketStatus status, TicketPriority priority,
                                        AppUser assignee, AppUser closedBy, Long batchId) {
        Ticket t = new Ticket();
        t.client = project.getClient();
        t.project = project;
        t.sourceSystem = sourceSystem;
        t.externalTicketId = v.externalTicketId();
        t.importChanges(v, status, priority, assignee, closedBy, batchId, true);
        return t;
    }

    /**
     * Compare this ticket with a row of an import file.
     * Returns the fields that differ. When apply = true the new values are also stored
     * (apply = false is used by "check only" so nothing is changed).
     */
    public List<FieldChange> importChanges(ImportedTicketValues v, TicketStatus newStatus, TicketPriority newPriority,
                                           AppUser newAssignee, AppUser newClosedBy, Long batchId, boolean apply) {
        List<FieldChange> c = new ArrayList<>();
        diff(c, "status", status == null ? null : status.getCode(), newStatus.getCode());
        diff(c, "priority", priority == null ? null : priority.getCode(), newPriority == null ? null : newPriority.getCode());
        diff(c, "assignee", userId(assignee), userId(newAssignee));
        diff(c, "assigneeName", assigneeNameRaw, v.assigneeName());
        diff(c, "closedBy", userId(closedBy), userId(newClosedBy));
        diff(c, "closedByName", closedByNameRaw, v.closedByName());
        diff(c, "requesterName", requesterName, v.requesterName());
        diff(c, "requesterRole", requesterRole, v.requesterRole());
        diff(c, "channel", channel, v.channel());
        diff(c, "escalated", String.valueOf(escalated), String.valueOf(v.escalated()));
        diff(c, "satisfactionRating", satisfactionRating, v.satisfactionRating());
        diff(c, "issueType", issueType, v.issueType());
        diff(c, "plan", plan, v.plan());
        diff(c, "topic", topic, v.topic());
        diff(c, "category", category, v.category());
        diff(c, "subcategory", subcategory, v.subcategory());
        diff(c, "description", description, v.description());
        diff(c, "createdAtSource", text(createdAtSource), text(v.createdAtSource()));
        diff(c, "resolvedAt", text(resolvedAt), text(v.resolvedAt()));
        diff(c, "closedAt", text(closedAt), text(v.closedAt()));
        diff(c, "timeframe", timeframeRaw, v.timeframeRaw());
        diff(c, "day", dayRaw, v.dayRaw());
        diff(c, "resolutionTime", resolutionTimeRaw, v.resolutionTimeRaw());
        diff(c, "srNo", importRowNumber == null ? null : importRowNumber.toString(),
                v.importRowNumber() == null ? null : v.importRowNumber().toString());
        if (apply && (!c.isEmpty() || id == null)) {
            status = newStatus;
            priority = newPriority;
            assignee = newAssignee;
            assigneeNameRaw = v.assigneeName();
            closedBy = newClosedBy;
            closedByNameRaw = v.closedByName();
            requesterName = v.requesterName();
            requesterRole = v.requesterRole();
            channel = v.channel();
            escalated = v.escalated();
            satisfactionRating = v.satisfactionRating();
            issueType = v.issueType();
            plan = v.plan();
            topic = v.topic();
            category = v.category();
            subcategory = v.subcategory();
            description = v.description();
            createdAtSource = v.createdAtSource();
            resolvedAt = v.resolvedAt();
            closedAt = v.closedAt();
            timeframeRaw = v.timeframeRaw();
            dayRaw = v.dayRaw();
            resolutionTimeRaw = v.resolutionTimeRaw();
            importRowNumber = v.importRowNumber();
            importBatchId = batchId;
            Instant last = v.createdAtSource();
            if (v.resolvedAt() != null && v.resolvedAt().isAfter(last)) {
                last = v.resolvedAt();
            }
            if (v.closedAt() != null && v.closedAt().isAfter(last)) {
                last = v.closedAt();
            }
            updatedAtSource = last;
        }
        return c;
    }

    private static void diff(List<FieldChange> changes, String field, String oldValue, String newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.add(new FieldChange(field, oldValue, newValue));
        }
    }

    private static String userId(AppUser u) {
        return u == null ? null : String.valueOf(u.getId());
    }

    private static String text(Instant i) {
        return i == null ? null : i.toString();
    }

    /** One changed field (for the ticket history). */
    public record FieldChange(String field, String oldValue, String newValue) {
    }

    /**
     * Change the status and keep the times correct:
     *  - leaving OPEN for the first time sets "first response"
     *  - a RESOLVED status sets "resolved at"
     *  - a CLOSED-group status (Closed, Merged) sets "closed at" and "closed by"
     *  - going back to an open/pending status clears resolved/closed (history keeps the old values)
     */
    public void changeStatus(TicketStatus newStatus, AppUser actor, Instant now) {
        if (firstResponseAt == null && "OPEN".equals(status.getCode()) && !"OPEN".equals(newStatus.getCode())) {
            firstResponseAt = now;
        }
        String group = newStatus.getStatusGroup();
        if ("RESOLVED".equals(group)) {
            if (resolvedAt == null) {
                resolvedAt = now;
            }
            closedAt = null;
            closedBy = null;
            closedByNameRaw = null;
        } else if ("CLOSED".equals(group)) {
            if (closedAt == null) {
                closedAt = now;
                closedBy = actor;
                closedByNameRaw = null;
            }
        } else {
            resolvedAt = null;
            closedAt = null;
            closedBy = null;
            closedByNameRaw = null;
        }
        status = newStatus;
        updatedAtSource = now;
    }

    /** Assign to an employee (or to nobody when null). */
    public void assignTo(AppUser newAssignee, Instant now) {
        assignee = newAssignee;
        assigneeNameRaw = null;
        updatedAtSource = now;
    }

    /** Edit the descriptive fields. Returns the list of fields that really changed. */
    public List<FieldChange> applyEdit(TicketChangeDtos.TicketUpdateRequest r, TicketPriority newPriority, Instant now) {
        List<FieldChange> changes = new ArrayList<>();
        subject = track(changes, "subject", subject, clean(r.subject()));
        description = track(changes, "description", description, clean(r.description()));
        category = track(changes, "category", category, clean(r.category()));
        subcategory = track(changes, "subcategory", subcategory, clean(r.subcategory()));
        issueType = track(changes, "issueType", issueType, clean(r.issueType()));
        topic = track(changes, "topic", topic, clean(r.topic()));
        plan = track(changes, "plan", plan, clean(r.plan()));
        channel = track(changes, "channel", channel, clean(r.channel()));
        requesterName = track(changes, "requesterName", requesterName, clean(r.requesterName()));
        requesterRole = track(changes, "requesterRole", requesterRole, clean(r.requesterRole()));
        String oldPriority = priority == null ? null : priority.getCode();
        String newPriorityCode = newPriority == null ? null : newPriority.getCode();
        if (!Objects.equals(oldPriority, newPriorityCode)) {
            changes.add(new FieldChange("priority", oldPriority, newPriorityCode));
            priority = newPriority;
        }
        boolean newEscalated = Boolean.TRUE.equals(r.escalated());
        if (escalated != newEscalated) {
            changes.add(new FieldChange("escalated", String.valueOf(escalated), String.valueOf(newEscalated)));
            escalated = newEscalated;
        }
        if (!changes.isEmpty()) {
            updatedAtSource = now;
        }
        return changes;
    }

    private static String track(List<FieldChange> changes, String field, String oldValue, String newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.add(new FieldChange(field, oldValue, newValue));
        }
        return newValue;
    }

    static String clean(String value) {
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
    public Project getProject() { return project; }
    public String getSourceSystem() { return sourceSystem; }
    public String getExternalTicketId() { return externalTicketId; }
    public String getSubject() { return subject; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getSubcategory() { return subcategory; }
    public String getIssueType() { return issueType; }
    public String getTopic() { return topic; }
    public String getPlan() { return plan; }
    public TicketPriority getPriority() { return priority; }
    public TicketStatus getStatus() { return status; }
    public AppUser getAssignee() { return assignee; }
    public String getAssigneeNameRaw() { return assigneeNameRaw; }
    public AppUser getClosedBy() { return closedBy; }
    public String getClosedByNameRaw() { return closedByNameRaw; }
    public String getRequesterName() { return requesterName; }
    public String getRequesterRole() { return requesterRole; }
    public String getChannel() { return channel; }
    public boolean isEscalated() { return escalated; }
    public String getSatisfactionRating() { return satisfactionRating; }
    public Instant getCreatedAtSource() { return createdAtSource; }
    public Instant getUpdatedAtSource() { return updatedAtSource; }
    public Instant getFirstResponseAt() { return firstResponseAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getSlaDueAt() { return slaDueAt; }
    public String getTimeframeRaw() { return timeframeRaw; }
    public String getDayRaw() { return dayRaw; }
    public String getResolutionTimeRaw() { return resolutionTimeRaw; }
    public Integer getImportRowNumber() { return importRowNumber; }
    public Long getImportBatchId() { return importBatchId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Integer getVersion() { return version; }
}