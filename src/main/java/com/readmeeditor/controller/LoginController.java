package com.readmeeditor.controller;

import com.readmeeditor.model.User;
import com.readmeeditor.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Controller handling user authentication and registration.
 * <p>
 * Mediates between the Login view and the UserService.
 */
public class LoginController {
    private static final Logger LOG = LoggerFactory.getLogger(LoginController.class);

    private final UserService userService;
    private User currentUser;

    public LoginController() {
        this.userService = new UserService();
    }

    public LoginController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Authenticates a user with the given credentials.
     *
     * @param username the username
     * @param password the plaintext password
     * @return true if authentication succeeded
     * @throws IllegalArgumentException if credentials are invalid
     */
    public boolean login(String username, String password) {
        currentUser = userService.authenticate(username, password);
        LOG.info("Login successful: {}", username);
        return true;
    }

    /**
     * Registers a new user.
     *
     * @param username the desired username
     * @param password the plaintext password
     * @param email    the user's email address
     * @return true if registration succeeded
     * @throws IllegalArgumentException if validation fails
     */
    public boolean register(String username, String password, String email) {
        currentUser = userService.registerUser(username, password, email);
        LOG.info("Registration successful: {}", username);
        return true;
    }

    /**
     * Returns the currently authenticated user, or null if not logged in.
     */
    public User getCurrentUser() {
        return currentUser;
    }

    /**
     * Checks if a user is currently authenticated.
     */
    public boolean isLoggedIn() {
        return currentUser != null;
    }

    /**
     * Logs out the current user.
     */
    public void logout() {
        if (currentUser != null) {
            LOG.info("User logged out: {}", currentUser.getUsername());
            currentUser = null;
        }
    }

    /**
     * Finds a user by ID.
     */
    public Optional<User> findUserById(String userId) {
        return userService.findUserById(userId);
    }

    /**
     * Finds a user by username.
     */
    public Optional<User> findUserByUsername(String username) {
        return userService.findUserByUsername(username);
    }
}
