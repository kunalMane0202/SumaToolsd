package com.sumasoft.tracking.auth;

import com.sumasoft.tracking.user.AppUser;
import com.sumasoft.tracking.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

/**
 * Gives the FIRST Super Admin a temporary password, from two terminal variables:
 *   SUMA_BOOTSTRAP_ADMIN_EMAIL     (an existing ACTIVE Super Admin)
 *   SUMA_BOOTSTRAP_ADMIN_PASSWORD  (temporary; must be changed at first login)
 * It only works if that Super Admin has NO password yet, so it can never overwrite a real password.
 * The password is never written to the log.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = System.getenv("SUMA_BOOTSTRAP_ADMIN_EMAIL");
        String password = System.getenv("SUMA_BOOTSTRAP_ADMIN_PASSWORD");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        Optional<AppUser> found = userRepository.findByEmailIgnoreCase(email.trim());
        if (found.isEmpty()) {
            log.warn("Bootstrap admin: no employee has that email. Nothing was changed.");
            return;
        }
        AppUser admin = found.get();
        if (!"SUPER_ADMIN".equals(admin.getRole().getCode()) || !"ACTIVE".equals(admin.getStatus())) {
            log.warn("Bootstrap admin: that employee is not an ACTIVE Super Admin. Nothing was changed.");
            return;
        }
        if (admin.hasPassword()) {
            log.info("Bootstrap admin: this Super Admin already has a password. Nothing was changed.");
            return;
        }
        try {
            AuthSupport.checkPasswordRules(password, admin.getEmail());
        } catch (ResponseStatusException e) {
            log.warn("Bootstrap admin: password not accepted: {}. Nothing was changed.", e.getReason());
            return;
        }
        admin.setPasswordHash(passwordEncoder.encode(password), true, Instant.now());
        userRepository.save(admin);
        log.info("Bootstrap admin: temporary password set for Super Admin id {}. Log in and change it.", admin.getId());
    }
}