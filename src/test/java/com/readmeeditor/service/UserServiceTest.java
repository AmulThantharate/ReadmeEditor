package com.readmeeditor.service;

import com.readmeeditor.model.Role;
import com.readmeeditor.model.User;
import com.readmeeditor.repository.UserRepository;
import com.readmeeditor.util.PasswordUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserService}.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    void registerUser_shouldCreateUserWithHashedPassword() {
        // Arrange
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User user = userService.registerUser("newuser", "password123!", "newuser@example.com");

        // Assert
        assertNotNull(user.getId());
        assertEquals("newuser", user.getUsername());
        assertEquals("newuser@example.com", user.getEmail());
        assertEquals(Role.USER, user.getRole());
        assertNotNull(user.getPasswordHash());
        assertNotEquals("password123!", user.getPasswordHash()); // Must be hashed
        assertNotNull(user.getCreatedAt());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void registerUser_withExistingUsername_shouldThrow() {
        // Arrange
        when(userRepository.existsByUsername("existing")).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> userService.registerUser("existing", "password123!", "e@e.com"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUser_withNullUsername_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> userService.registerUser(null, "password123!", "e@e.com"));
    }

    @Test
    void registerUser_withShortPassword_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> userService.registerUser("user", "short", "e@e.com"));
    }

    @Test
    void authenticate_withCorrectCredentials_shouldSucceed() {
        // Arrange: Create a user with a real BCrypt hash
        String realPassword = "correct-password-123";
        String realHash = PasswordUtils.hashPassword(realPassword);
        User user = new User("1", "testuser", realHash, "test@test.com");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        // Act
        User authenticated = userService.authenticate("testuser", realPassword);

        // Assert
        assertNotNull(authenticated);
        assertEquals("testuser", authenticated.getUsername());
        assertEquals("test@test.com", authenticated.getEmail());
        verify(userRepository, times(1)).updateLastLogin(eq("1"), any());
    }

    @Test
    void authenticate_withWrongPassword_shouldThrow() {
        // Arrange
        String hash = PasswordUtils.hashPassword("correct-password");
        User user = new User("1", "testuser", hash, "test@test.com");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> userService.authenticate("testuser", "wrong-password"));
    }

    @Test
    void authenticate_withUnknownUsername_shouldThrow() {
        // Arrange
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> userService.authenticate("unknown", "any-password"));
    }

    @Test
    void findUserById_shouldReturnUser() {
        // Arrange
        User user = new User("1", "founduser", "hash", "found@test.com");
        when(userRepository.findById("1")).thenReturn(Optional.of(user));

        // Act
        Optional<User> result = userService.findUserById("1");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("founduser", result.get().getUsername());
    }

    @Test
    void deleteUser_shouldCallRepository() {
        // Act
        userService.deleteUser("user1");

        // Assert
        verify(userRepository, times(1)).deleteById("user1");
    }
}
