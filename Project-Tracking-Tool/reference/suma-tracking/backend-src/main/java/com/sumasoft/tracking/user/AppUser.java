package com.sumasoft.tracking.user;

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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Locale;

/**
 * One row of the "users" table (created by Flyway file V3): an employee.
 * Named AppUser (not User) to avoid confusion with the login library added later.
 * Login fields (V4): only a BCrypt HASH of the password is stored, never the password.
 */
@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, length = 30)
    private String employeeCode;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "contact_number", length = 30)
    private String contactNumber;

    @Column(name = "designation", length = 100)
    private String designation;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "skill_set", columnDefinition = "text")
    private String skillSet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporting_manager_id")
    private AppUser reportingManager;

    @Column(name = "shift_start")
    private LocalTime shiftStart;

    @Column(name = "shift_end")
    private LocalTime shiftEnd;

    /** For example SAT,SUN */
    @Column(name = "weekly_off", length = 30)
    private String weeklyOff;

    /** ACTIVE or INACTIVE (the database CHECK rule in V3 allows only these). */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    // ---------- Login fields (Flyway V4) ----------

    /** BCrypt hash of the password. Empty means "no password set yet" (cannot log in). */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "password_changed_at", columnDefinition = "timestamptz")
    private Instant passwordChangedAt;

    /** True for a new or reset password: the person must choose their own password first. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    /** After too many wrong passwords, login is blocked until this time. */
    @Column(name = "locked_until", columnDefinition = "timestamptz")
    private Instant lockedUntil;

    @Column(name = "last_login_at", columnDefinition = "timestamptz")
    private Instant lastLoginAt;

    /** Needed by JPA. Not for normal use. */
    protected AppUser() {
    }

    /** Make a new employee from checked input. New employees start as ACTIVE. */
    public static AppUser create(UserRequest request, Role role, AppUser reportingManager) {
        AppUser user = new AppUser();
        user.applyDetails(request, role, reportingManager);
        user.status = "ACTIVE";
        return user;
    }

    /** Copy the editable fields from checked input. */
    public void applyDetails(UserRequest request, Role role, AppUser reportingManager) {
        this.employeeCode = clean(request.employeeCode());
        this.fullName = clean(request.fullName());
        String mail = clean(request.email());
        this.email = mail == null ? null : mail.toLowerCase(Locale.ROOT);
        this.contactNumber = clean(request.contactNumber());
        this.designation = clean(request.designation());
        this.department = clean(request.department());
        this.skillSet = clean(request.skillSet());
        this.role = role;
        this.reportingManager = reportingManager;
        this.shiftStart = request.shiftStart();
        this.shiftEnd = request.shiftEnd();
        String off = clean(request.weeklyOff());
        this.weeklyOff = off == null ? null : off.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    /** Deactivating never deletes the employee or their history. */
    public void activate() {
        this.status = "ACTIVE";
    }

    public void deactivate() {
        this.status = "INACTIVE";
    }

    // ---------- Login helpers ----------

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Count a wrong password. Returns true if this attempt locked the account. */
    public boolean recordFailedLogin(Instant now, int maxAttempts, Duration lockTime) {
        this.failedLoginCount++;
        if (this.failedLoginCount >= maxAttempts) {
            this.lockedUntil = now.plus(lockTime);
            this.failedLoginCount = 0;
            return true;
        }
        return false;
    }

    public void recordSuccessfulLogin(Instant now) {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
        this.lastLoginAt = now;
    }

    /**
     * Store a new password HASH.
     * mustChange = true for a temporary password (first login or admin reset).
     */
    public void setPasswordHash(String newHash, boolean mustChange, Instant now) {
        this.passwordHash = newHash;
        this.mustChangePassword = mustChange;
        this.passwordChangedAt = now;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
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
    public String getEmployeeCode() { return employeeCode; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getContactNumber() { return contactNumber; }
    public String getDesignation() { return designation; }
    public String getDepartment() { return department; }
    public String getSkillSet() { return skillSet; }
    public Role getRole() { return role; }
    public AppUser getReportingManager() { return reportingManager; }
    public LocalTime getShiftStart() { return shiftStart; }
    public LocalTime getShiftEnd() { return shiftEnd; }
    public String getWeeklyOff() { return weeklyOff; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Integer getVersion() { return version; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getPasswordChangedAt() { return passwordChangedAt; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
}