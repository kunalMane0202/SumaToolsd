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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;

/**
 * One row of the "user_sessions" table (Flyway V4): one login.
 * Only the SHA-256 fingerprint of the token is stored, never the token.
 */
@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant expiresAt;

    @Column(name = "last_used_at", columnDefinition = "timestamptz")
    private Instant lastUsedAt;

    @Column(name = "revoked_at", columnDefinition = "timestamptz")
    private Instant revokedAt;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 300, updatable = false)
    private String userAgent;

    /** Needed by JPA. Not for normal use. */
    protected UserSession() {
    }

    public static UserSession start(AppUser user, String tokenHash, Instant now, Duration maxLife,
                                    String ipAddress, String userAgent) {
        UserSession s = new UserSession();
        s.user = user;
        s.tokenHash = tokenHash;
        s.createdAt = now;
        s.lastUsedAt = now;
        s.expiresAt = now.plus(maxLife);
        s.ipAddress = AuthSupport.clip(ipAddress, 45);
        s.userAgent = AuthSupport.clip(userAgent, 300);
        return s;
    }

    /** Can this session still be used? Not logged out, not past its end, not idle for too long. */
    public boolean isUsable(Instant now, Duration maxIdle) {
        if (revokedAt != null || !expiresAt.isAfter(now)) {
            return false;
        }
        Instant lastActivity = lastUsedAt != null ? lastUsedAt : createdAt;
        return lastActivity.plus(maxIdle).isAfter(now);
    }

    public void touch(Instant now) {
        this.lastUsedAt = now;
    }

    public void revoke(Instant now) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getRevokedAt() { return revokedAt; }
}