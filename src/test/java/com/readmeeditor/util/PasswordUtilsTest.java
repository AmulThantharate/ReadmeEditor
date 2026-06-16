package com.readmeeditor.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PasswordUtils}.
 */
class PasswordUtilsTest {

    @Test
    void hashPassword_shouldReturnHash() {
        // Act
        String hash = PasswordUtils.hashPassword("myPassword123!");

        // Assert
        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"));
        assertNotEquals("myPassword123!", hash);
    }

    @Test
    void checkPassword_withCorrectPassword_shouldReturnTrue() {
        // Arrange
        String password = "correct-password-123";
        String hash = PasswordUtils.hashPassword(password);

        // Act
        boolean result = PasswordUtils.checkPassword(password, hash);

        // Assert
        assertTrue(result);
    }

    @Test
    void checkPassword_withWrongPassword_shouldReturnFalse() {
        // Arrange
        String hash = PasswordUtils.hashPassword("correct-password");

        // Act
        boolean result = PasswordUtils.checkPassword("wrong-password", hash);

        // Assert
        assertFalse(result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void checkPassword_withNullOrEmpty_shouldReturnFalse(String password) {
        // Act
        boolean result = PasswordUtils.checkPassword(password, "$2a$12$hashhashhashhashhashhashhashhashhash");

        // Assert
        assertFalse(result);
    }

    @Test
    void checkPassword_withNullHash_shouldReturnFalse() {
        assertFalse(PasswordUtils.checkPassword("password", null));
    }

    @Test
    void hashPassword_withNull_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordUtils.hashPassword(null));
    }

    @Test
    void hashPassword_withEmpty_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordUtils.hashPassword(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "1234567", "tiny"})
    void isValidLength_withShortPasswords_shouldReturnFalse(String shortPassword) {
        assertFalse(PasswordUtils.isValidLength(shortPassword));
    }

    @Test
    void isValidLength_withLongEnoughPassword_shouldReturnTrue() {
        assertTrue(PasswordUtils.isValidLength("long-enough-password"));
    }
}
