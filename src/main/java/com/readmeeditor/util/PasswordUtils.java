package com.readmeeditor.util;

import com.readmeeditor.config.AppConfig;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for password hashing and validation.
 * <p>
 * Uses BCrypt with configurable workload factor for secure password storage.
 */
public final class PasswordUtils {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordUtils.class);

    private PasswordUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Hashes a plaintext password using BCrypt.
     *
     * @param plainPassword the plaintext password (must meet minimum length requirements)
     * @return the BCrypt hash string
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        int rounds = AppConfig.getInstance().getBcryptRounds();
        String salt = BCrypt.gensalt(rounds);
        String hash = BCrypt.hashpw(plainPassword, salt);
        LOG.debug("Password hashed with {} BCrypt rounds", rounds);
        return hash;
    }

    /**
     * Validates a plaintext password against a BCrypt hash.
     *
     * @param plainPassword the plaintext password to check
     * @param hashedPassword the stored BCrypt hash
     * @return true if the password matches the hash
     */
    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (Exception e) {
            LOG.error("Password validation failed", e);
            return false;
        }
    }

    /**
     * Validates that a password meets the minimum length requirement.
     *
     * @param password the password to validate
     * @return true if the password meets the minimum length
     */
    public static boolean isValidLength(String password) {
        int minLength = AppConfig.getInstance().getPasswordMinLength();
        return password != null && password.length() >= minLength;
    }
}
