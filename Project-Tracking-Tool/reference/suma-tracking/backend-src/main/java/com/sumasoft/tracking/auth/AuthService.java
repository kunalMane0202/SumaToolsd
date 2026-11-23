package com.sumasoft.tracking.auth;

import com.sumasoft.tracking.audit.AuditService;
import com.sumasoft.tracking.auth.AuthDtos.ChangePasswordRequest;
import com.sumasoft.tracking.auth.AuthDtos.LoginRequest;
import com.sumasoft.tracking.auth.AuthDtos.LoginResponse;
import com.sumasoft.tracking.auth.AuthDtos.MeResponse;
import com.sumasoft.tracking.auth.AuthDtos.MessageResponse;
import com.sumasoft.tracking.auth.AuthDtos.ResetPasswordRequest;
import com.sumasoft.tracking.user.AppUser;
import com.sumasoft.tracking.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Login, logout, "who am I", password change, admin password reset and access revoke.
 *
 * Safety rules:
 *  - Passwords are checked with BCrypt; only the hash is stored.
 *  - The same error message for every failed login (wrong email, wrong password, locked,
 *    inactive), so nobody can find out which emails exist.
 *  - 5 wrong passwords lock the account for 15 minutes.
 *  - A session ends after 8 hours, or after 60 minutes without use, or at logout.
 * Audit (Step 26): password changes, admin password resets and access revokes are also recorded
 * in the audit history (who did it to whom). No password or token is ever written there.
 */
@Service
public class AuthService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCK_TIME = Duration.ofMinutes(15);
    static final Duration SESSION_MAX_LIFE = Duration.ofHours(8);
    static final Duration SESSION_MAX_IDLE = Duration.ofMinutes(60);
    private static final Duration TOUCH_EVERY = Duration.ofMinutes(1);

    private static final String LOGIN_FAILED_MESSAGE =
            "Login failed. Check your email and password. After 5 wrong attempts the account is locked for 15 minutes.";

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final LoginHistoryRepository historyRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    /** Used to spend the same time on unknown emails as on real ones. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository,
                       UserSessionRepository sessionRepository,
                       LoginHistoryRepository historyRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService audit) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.historyRepository = historyRepository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.dummyHash = passwordEncoder.encode("timing-dummy-password-123");
    }

    // ---------------------------------------------------------------- login

    /** noRollbackFor: failed attempts and lock-outs must be SAVED even though we answer with an error. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        Instant now = Instant.now();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String password = request.password();

        AppUser user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            spendTime(password);
            record(null, email, "LOGIN_FAILED", ipAddress, userAgent, now);
            throw loginFailed();
        }
        if (user.isLocked(now)) {
            record(user, email, "LOGIN_LOCKED", ipAddress, userAgent, now);
            throw loginFailed();
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            spendTime(password);
            record(user, email, "LOGIN_INACTIVE", ipAddress, userAgent, now);
            throw loginFailed();
        }
        if (!user.hasPassword()) {
            spendTime(password);
            record(user, email, "LOGIN_NO_PASSWORD", ipAddress, userAgent, now);
            throw loginFailed();
        }
        if (AuthSupport.tooLongForBcrypt(password) || !passwordEncoder.matches(password, user.getPasswordHash())) {
            boolean lockedNow = user.recordFailedLogin(now, MAX_FAILED_ATTEMPTS, LOCK_TIME);
            userRepository.save(user);
            record(user, email, lockedNow ? "LOGIN_LOCKED" : "LOGIN_FAILED", ipAddress, userAgent, now);
            throw loginFailed();
        }

        user.recordSuccessfulLogin(now);
        userRepository.save(user);
        String token = AuthSupport.newToken();
        UserSession session = sessionRepository.save(
                UserSession.start(user, AuthSupport.sha256Hex(token), now, SESSION_MAX_LIFE, ipAddress, userAgent));
        record(user, email, "LOGIN_SUCCESS", ipAddress, userAgent, now);
        return new LoginResponse(token, session.getExpiresAt(), user.isMustChangePassword(), MeResponse.from(user));
    }

    // ---------------------------------------------------------------- every request

    /**
     * Turns a token from the "Authorization: Bearer ..." header into "who is calling".
     * Empty if the token is unknown, logged out, expired, idle too long, or the employee is inactive.
     */
    @Transactional
    public Optional<AuthenticatedUser> authenticateToken(String token) {
        if (!AuthSupport.looksLikeToken(token)) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        UserSession session = sessionRepository.findByTokenHash(AuthSupport.sha256Hex(token)).orElse(null);
        if (session == null || !session.isUsable(now, SESSION_MAX_IDLE)) {
            return Optional.empty();
        }
        AppUser user = session.getUser();
        if (!"ACTIVE".equals(user.getStatus())) {
            session.revoke(now);
            return Optional.empty();
        }
        if (session.getLastUsedAt() == null || session.getLastUsedAt().plus(TOUCH_EVERY).isBefore(now)) {
            session.touch(now);
        }
        return Optional.of(new AuthenticatedUser(
                user.getId(), session.getId(), user.getEmail(), user.getFullName(),
                user.getRole().getCode(), user.getRole().getUserGroup(), user.isMustChangePassword()));
    }

    // ---------------------------------------------------------------- logged-in actions

    @Transactional(readOnly = true)
    public MeResponse me(AuthenticatedUser caller) {
        return MeResponse.from(findUser(caller.userId()));
    }

    @Transactional
    public MessageResponse logout(AuthenticatedUser caller, String ipAddress, String userAgent) {
        Instant now = Instant.now();
        sessionRepository.findById(caller.sessionId()).ifPresent(s -> s.revoke(now));
        record(findUser(caller.userId()), caller.email(), "LOGOUT", ipAddress, userAgent, now);
        return new MessageResponse("Logged out");
    }

    @Transactional
    public MessageResponse changePassword(AuthenticatedUser caller, ChangePasswordRequest request,
                                          String ipAddress, String userAgent) {
        Instant now = Instant.now();
        AppUser user = findUser(caller.userId());
        if (AuthSupport.tooLongForBcrypt(request.currentPassword())
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is not correct");
        }
        AuthSupport.checkPasswordRules(request.newPassword(), user.getEmail());
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "New password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()), false, now);
        userRepository.save(user);
        // Stay logged in here, but log out every OTHER device.
        sessionRepository.revokeOthersForUser(user.getId(), caller.sessionId(), now);
        record(user, user.getEmail(), "PASSWORD_CHANGED", ipAddress, userAgent, now);
        audit.record("USER_PASSWORD_CHANGED", "USER", user.getId(), null, null, null,
                facts("otherSessionsEnded", true));
        return new MessageResponse("Password changed");
    }

    // ---------------------------------------------------------------- Super Admin actions (FR-006)

    /** Super Admin gives an employee a TEMPORARY password. They must change it at next login. */
    @Transactional
    public MessageResponse resetPassword(AuthenticatedUser caller, Long userId, ResetPasswordRequest request,
                                         String ipAddress, String userAgent) {
        requireSuperAdmin(caller);
        Instant now = Instant.now();
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        AuthSupport.checkPasswordRules(request.newPassword(), user.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()), true, now);
        userRepository.save(user);
        sessionRepository.revokeAllForUser(user.getId(), now);
        record(user, user.getEmail(), "PASSWORD_RESET", ipAddress, userAgent, now);
        audit.record("USER_PASSWORD_RESET", "USER", user.getId(), null, null, null,
                facts("mustChangePassword", true));
        return new MessageResponse("Temporary password set. The employee must change it at next login");
    }

    /** Super Admin logs an employee out of every device immediately. */
    @Transactional
    public MessageResponse revokeAccess(AuthenticatedUser caller, Long userId, String ipAddress, String userAgent) {
        requireSuperAdmin(caller);
        Instant now = Instant.now();
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        int count = sessionRepository.revokeAllForUser(user.getId(), now);
        record(user, user.getEmail(), "ACCESS_REVOKED", ipAddress, userAgent, now);
        audit.record("USER_ACCESS_REVOKED", "USER", user.getId(), null, null, null,
                facts("sessionsEnded", count));
        return new MessageResponse("Access revoked. Sessions ended: " + count);
    }

    // ---------------------------------------------------------------- helpers

    private void requireSuperAdmin(AuthenticatedUser caller) {
        if (caller == null || !caller.isSuperAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a Super Admin can do this");
        }
    }

    private AppUser findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in again"));
    }

    private void spendTime(String password) {
        if (!AuthSupport.tooLongForBcrypt(password)) {
            passwordEncoder.matches(password, dummyHash);
        }
    }

    private void record(AppUser user, String email, String event, String ip, String userAgent, Instant now) {
        historyRepository.save(LoginHistory.of(user, email, event, ip, userAgent, now));
    }

    /** A tiny "fact" for the audit history (never a password or token). */
    private static Map<String, Object> facts(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static ResponseStatusException loginFailed() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, LOGIN_FAILED_MESSAGE);
    }
}