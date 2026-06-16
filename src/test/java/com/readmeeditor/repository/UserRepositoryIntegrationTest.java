package com.readmeeditor.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.readmeeditor.config.RedisConfig;
import com.readmeeditor.model.Role;
import com.readmeeditor.model.User;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link UserRepository} using Testcontainers
 * with a real Redis instance.
 */
@Testcontainers(disabledWithoutDocker = true)
class UserRepositoryIntegrationTest {

    @Container
    private static final GenericContainer<?> redisContainer = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static RedisConfig redisConfig;
    private UserRepository repository;
    private ObjectMapper objectMapper;

    @BeforeAll
    static void setupRedisConfig() {
        redisContainer.start();
        System.setProperty("redis.host", redisContainer.getHost());
        System.setProperty("redis.port", String.valueOf(redisContainer.getMappedPort(6379)));
        System.setProperty("redis.password", "");
        System.setProperty("redis.timeout", "3000");
        System.setProperty("redis.database", "2");

        redisConfig = RedisConfig.getInstance();
    }

    @AfterAll
    static void cleanup() {
        redisContainer.stop();
        System.clearProperty("redis.host");
        System.clearProperty("redis.port");
        System.clearProperty("redis.password");
        System.clearProperty("redis.timeout");
        System.clearProperty("redis.database");
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        repository = new UserRepository(redisConfig, objectMapper);

        try (var jedis = redisConfig.getConnection()) {
            jedis.flushDB();
        }
    }

    @Test
    void save_and_findById_shouldWork() {
        // Arrange
        User user = new User(UUID.randomUUID().toString(), "johndoe",
                "$2a$12$hash", "john@example.com");

        // Act
        repository.save(user);
        Optional<User> retrieved = repository.findById(user.getId());

        // Assert
        assertTrue(retrieved.isPresent());
        assertEquals("johndoe", retrieved.get().getUsername());
        assertEquals("john@example.com", retrieved.get().getEmail());
        assertEquals(Role.USER, retrieved.get().getRole());
    }

    @Test
    void findByUsername_shouldReturnCorrectUser() {
        // Arrange
        User user = new User(UUID.randomUUID().toString(), "janedoe",
                "$2a$12$hash", "jane@example.com");
        repository.save(user);

        // Act
        Optional<User> retrieved = repository.findByUsername("janedoe");

        // Assert
        assertTrue(retrieved.isPresent());
        assertEquals(user.getId(), retrieved.get().getId());
    }

    @Test
    void findByUsername_shouldBeCaseInsensitive() {
        // Arrange
        User user = new User(UUID.randomUUID().toString(), "CaseUser",
                "$2a$12$hash", "case@example.com");
        repository.save(user);

        // Act
        Optional<User> retrieved = repository.findByUsername("caseuser");

        // Assert
        assertTrue(retrieved.isPresent());
        assertEquals("CaseUser", retrieved.get().getUsername());
    }

    @Test
    void existsByUsername_shouldReturnTrueForExisting() {
        // Arrange
        repository.save(new User("1", "existing", "hash", "e@e.com"));

        // Act & Assert
        assertTrue(repository.existsByUsername("existing"));
        assertFalse(repository.existsByUsername("nonexistent"));
    }

    @Test
    void findAll_shouldReturnAllUsers() {
        // Arrange
        repository.save(new User("1", "user1", "hash1", "u1@e.com"));
        repository.save(new User("2", "user2", "hash2", "u2@e.com"));
        repository.save(new User("3", "user3", "hash3", "u3@e.com"));

        // Act
        List<User> users = repository.findAll();

        // Assert
        assertEquals(3, users.size());
    }

    @Test
    void deleteById_shouldRemoveUser() {
        // Arrange
        String id = UUID.randomUUID().toString();
        repository.save(new User(id, "todelete", "hash", "del@e.com"));
        assertTrue(repository.findById(id).isPresent());

        // Act
        repository.deleteById(id);

        // Assert
        assertFalse(repository.findById(id).isPresent());
        assertFalse(repository.existsByUsername("todelete"));
    }

    @Test
    void count_shouldReturnCorrectCount() {
        // Arrange
        repository.save(new User("1", "a", "hash", "a@e.com"));
        repository.save(new User("2", "b", "hash", "b@e.com"));

        // Act & Assert
        assertEquals(2, repository.count());
    }

    @Test
    void updateLastLogin_shouldPersistTimestamp() {
        // Arrange
        String id = UUID.randomUUID().toString();
        repository.save(new User(id, "logintest", "hash", "login@e.com"));
        LocalDateTime loginTime = LocalDateTime.of(2024, 6, 15, 10, 30, 0);

        // Act
        repository.updateLastLogin(id, loginTime);
        Optional<User> retrieved = repository.findById(id);

        // Assert
        assertTrue(retrieved.isPresent());
        assertEquals(loginTime, retrieved.get().getLastLoginAt());
    }
}
