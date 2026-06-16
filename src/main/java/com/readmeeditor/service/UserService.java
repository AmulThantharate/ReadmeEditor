package com.readmeeditor.service;

import com.readmeeditor.model.Role;
import com.readmeeditor.model.User;
import com.readmeeditor.repository.UserRepository;
import com.readmeeditor.util.PasswordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for user management.
 * <p>
 * Handles user registration, authentication, and profile management.
 * Passwords are hashed using BCrypt via {@link PasswordUtils}.
 */
public class UserService {
    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService() {
        this.userRepository = new UserRepository();
    }

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Registers a new user.
     *
     * @param username the desired username
     * @param password the plaintext password (will be hashed)
     * @param email    the user's email address
     * @return the created user
     * @throws IllegalArgumentException if the username is taken or password is too short
     */
    public User registerUser(String username, String password, String email) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (userRepository.existsByUsername(username.trim())) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken");
        }
        if (!PasswordUtils.isValidLength(password)) {
            throw new IllegalArgumentException(
                    "Password must be at least " + com.readmeeditor.config.AppConfig.getInstance()
                            .getPasswordMinLength() + " characters long");
        }

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(username.trim());
        user.setPasswordHash(PasswordUtils.hashPassword(password));
        user.setEmail(email != null ? email.trim() : "");
        user.setRole(Role.USER);
        user.setCreatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);
        LOG.info("User registered: {} (ID: {})", saved.getUsername(), saved.getId());
        return saved;
    }

    /**
     * Authenticates a user by username and password.
     *
     * @param username the username
     * @param password the plaintext password
     * @return the authenticated user
     * @throws IllegalArgumentException if credentials are invalid
     */
    public User authenticate(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            LOG.warn("Login failed: unknown username '{}'", username);
            throw new IllegalArgumentException("Invalid username or password");
        }

        User user = userOpt.get();
        if (!PasswordUtils.checkPassword(password, user.getPasswordHash())) {
            LOG.warn("Login failed: incorrect password for '{}'", username);
            throw new IllegalArgumentException("Invalid username or password");
        }

        userRepository.updateLastLogin(user.getId(), LocalDateTime.now());
        LOG.info("User logged in: {} (ID: {})", user.getUsername(), user.getId());
        return user;
    }

    /**
     * Finds a user by ID.
     */
    public Optional<User> findUserById(String id) {
        return userRepository.findById(id);
    }

    /**
     * Finds a user by username.
     */
    public Optional<User> findUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Returns all registered users.
     */
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Counts registered users.
     */
    public long countUsers() {
        return userRepository.count();
    }

    /**
     * Updates a user's profile information.
     *
     * @param user the user with updated fields
     * @return the saved user
     */
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    /**
     * Updates a user's password.
     *
     * @param userId      the user ID
     * @param oldPassword the current password for verification
     * @param newPassword the new password
     * @throws IllegalArgumentException if the old password is incorrect
     */
    public void changePassword(String userId, String oldPassword, String newPassword) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }

        User user = userOpt.get();
        if (!PasswordUtils.checkPassword(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPasswordHash(PasswordUtils.hashPassword(newPassword));
        userRepository.save(user);
        LOG.info("Password changed for user: {}", user.getUsername());
    }

    /**
     * Deletes a user account.
     */
    public void deleteUser(String userId) {
        userRepository.deleteById(userId);
    }
}
