package com.sumasoft.tracking.auth;

import com.sumasoft.tracking.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row of the "login_history" table (Flyway V4): one login event (FR-005).
 * Only added, never changed.
 */
@Entity
@Table(name = "login_history")
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empty when the email did not match any employee. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false)
    private AppUser user;

    @Column(name = "email_attempted", nullable = false, length = 254, updatable = false)
    private String emailAttempted;

    /** LOGIN_SUCCESS, LOGIN_FAILED, LOGIN_LOCKED, LOGOUT, ... (checked by the database). */
    @Column(name = "event", nullable = false, length = 30, updatable = false)
    private String event;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 300, updatable = false)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    /** Needed by JPA. Not for normal use. */
    protected LoginHistory() {
    }

    public static LoginHistory of(AppUser user, String emailAttempted, String event,
                                  String ipAddress, String userAgent, Instant now) {
        LoginHistory h = new LoginHistory();
        h.user = user;
        h.emailAttempted = AuthSupport.clip(emailAttempted == null ? "" : emailAttempted, 254);
        h.event = event;
        h.ipAddress = AuthSupport.clip(ipAddress, 45);
        h.userAgent = AuthSupport.clip(userAgent, 300);
        h.createdAt = now;
        return h;
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public String getEmailAttempted() { return emailAttempted; }
    public String getEvent() { return event; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
}