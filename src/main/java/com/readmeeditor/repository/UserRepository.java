package com.readmeeditor.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.readmeeditor.config.RedisConfig;
import com.readmeeditor.model.Role;
import com.readmeeditor.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Repository for managing User persistence in Redis.
 * <p>
 * Storage schema:
 * <ul>
 *   <li>{@code user:{id}} — Hash containing user fields</li>
 *   <li>{@code user:username:{username}} — String mapping username to user ID</li>
 *   <li>{@code user:all} — Set of all user IDs</li>
 * </ul>
 */
public class UserRepository {
    private static final Logger LOG = LoggerFactory.getLogger(UserRepository.class);
    private static final String KEY_PREFIX = "user:";
    private static final String USERNAME_INDEX_PREFIX = "user:username:";
    private static final String ALL_USERS_KEY = "user:all";

    private final RedisConfig redisConfig;
    private final ObjectMapper objectMapper;

    public UserRepository() {
        this.redisConfig = RedisConfig.getInstance();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    UserRepository(RedisConfig redisConfig, ObjectMapper objectMapper) {
        this.redisConfig = redisConfig;
        this.objectMapper = objectMapper;
    }

    /**
     * Saves a user to Redis. Creates or updates the user hash and username index.
     *
     * @param user the user to save
     * @return the saved user
     */
    public User save(User user) {
        Objects.requireNonNull(user, "User must not be null");
        Objects.requireNonNull(user.getId(), "User ID must not be null");

        try (Jedis jedis = redisConfig.getConnection()) {
            String key = KEY_PREFIX + user.getId();
            Map<String, String> hash = new HashMap<>();
            hash.put("username", user.getUsername());
            hash.put("passwordHash", user.getPasswordHash());
            hash.put("email", user.getEmail());
            hash.put("role", user.getRole().name());
            hash.put("createdAt", user.getCreatedAt().toString());
            hash.put("lastLoginAt", user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : "");

            jedis.hset(key, hash);
            jedis.set(USERNAME_INDEX_PREFIX + user.getUsername().toLowerCase(), user.getId());
            jedis.sadd(ALL_USERS_KEY, user.getId());

            LOG.debug("User saved: {} ({})", user.getUsername(), user.getId());
            return user;
        }
    }

    /**
     * Finds a user by their unique ID.
     *
     * @param id the user ID
     * @return an Optional containing the user, or empty if not found
     */
    public Optional<User> findById(String id) {
        Objects.requireNonNull(id, "User ID must not be null");

        try (Jedis jedis = redisConfig.getConnection()) {
            String key = KEY_PREFIX + id;
            Map<String, String> hash = jedis.hgetAll(key);
            if (hash.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(mapToUser(id, hash));
        }
    }

    /**
     * Finds a user by their username.
     *
     * @param username the username to look up
     * @return an Optional containing the user, or empty if not found
     */
    public Optional<User> findByUsername(String username) {
        Objects.requireNonNull(username, "Username must not be null");

        try (Jedis jedis = redisConfig.getConnection()) {
            String userId = jedis.get(USERNAME_INDEX_PREFIX + username.toLowerCase());
            if (userId == null) {
                return Optional.empty();
            }
            return findById(userId);
        }
    }

    /**
     * Checks if a username is already taken.
     *
     * @param username the username to check
     * @return true if the username exists
     */
    public boolean existsByUsername(String username) {
        try (Jedis jedis = redisConfig.getConnection()) {
            return jedis.exists(USERNAME_INDEX_PREFIX + username.toLowerCase());
        }
    }

    /**
     * Returns all registered users.
     *
     * @return a list of all users
     */
    public List<User> findAll() {
        try (Jedis jedis = redisConfig.getConnection()) {
            Set<String> userIds = jedis.smembers(ALL_USERS_KEY);
            List<User> users = new ArrayList<>();
            for (String id : userIds) {
                findById(id).ifPresent(users::add);
            }
            users.sort(Comparator.comparing(User::getCreatedAt).reversed());
            return users;
        }
    }

    /**
     * Counts total registered users.
     *
     * @return the total user count
     */
    public long count() {
        try (Jedis jedis = redisConfig.getConnection()) {
            return jedis.scard(ALL_USERS_KEY);
        }
    }

    /**
     * Deletes a user by ID.
     *
     * @param id the user ID to delete
     */
    public void deleteById(String id) {
        Objects.requireNonNull(id, "User ID must not be null");

        try (Jedis jedis = redisConfig.getConnection()) {
            Optional<User> user = findById(id);
            user.ifPresent(u -> {
                jedis.del(KEY_PREFIX + id);
                jedis.del(USERNAME_INDEX_PREFIX + u.getUsername().toLowerCase());
                jedis.srem(ALL_USERS_KEY, id);
                LOG.info("User deleted: {} ({})", u.getUsername(), id);
            });
        }
    }

    /**
     * Updates the last login timestamp for a user.
     *
     * @param userId      the user ID
     * @param lastLoginAt the timestamp of the login
     */
    public void updateLastLogin(String userId, LocalDateTime lastLoginAt) {
        try (Jedis jedis = redisConfig.getConnection()) {
            jedis.hset(KEY_PREFIX + userId, "lastLoginAt", lastLoginAt.toString());
        }
    }

    /**
     * Maps a Redis hash to a User object.
     */
    private User mapToUser(String id, Map<String, String> hash) {
        User user = new User();
        user.setId(id);
        user.setUsername(hash.getOrDefault("username", ""));
        user.setPasswordHash(hash.getOrDefault("passwordHash", ""));
        user.setEmail(hash.getOrDefault("email", ""));
        user.setRole(Role.valueOf(hash.getOrDefault("role", "USER")));
        user.setCreatedAt(LocalDateTime.parse(hash.getOrDefault("createdAt", LocalDateTime.now().toString())));

        String lastLoginStr = hash.get("lastLoginAt");
        if (lastLoginStr != null && !lastLoginStr.isEmpty()) {
            user.setLastLoginAt(LocalDateTime.parse(lastLoginStr));
        }

        return user;
    }
}
