package com.sumasoft.tracking.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Small security helpers: tokens, fingerprints and the password rules.
 */
public final class AuthSupport {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** BCrypt only uses the first 72 bytes of a password, so longer ones are refused. */
    public static final int MAX_PASSWORD_BYTES = 72;
    public static final int MIN_PASSWORD_LENGTH = 10;

    private AuthSupport() {
    }

    /** A new random login token: 32 random bytes = 43 URL-safe characters. */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Does this text look like one of our tokens? (Quick check before using the database.) */
    public static boolean looksLikeToken(String value) {
        return value != null && value.matches("^[A-Za-z0-9_-]{40,60}$");
    }

    /** SHA-256 fingerprint (64 hex characters). Only this is stored in the database. */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static boolean tooLongForBcrypt(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }

    /**
     * Password rules: at least 10 characters, at most 72 bytes,
     * at least one letter and one digit, and not the same as the email.
     */
    public static void checkPasswordRules(String password, String email) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw badPassword("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (tooLongForBcrypt(password)) {
            throw badPassword("Password is too long (maximum 72 bytes)");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*[0-9].*")) {
            throw badPassword("Password must contain at least one letter and one digit");
        }
        if (email != null && password.trim().toLowerCase(Locale.ROOT).equals(email.trim().toLowerCase(Locale.ROOT))) {
            throw badPassword("Password must not be the same as the email");
        }
    }

    /** Cut text to a maximum length (for IP address and browser name). */
    public static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static ResponseStatusException badPassword(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}