package com.readmeeditor.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.readmeeditor.config.RedisConfig;
import com.readmeeditor.model.ReadmeDocument;
import com.readmeeditor.model.ReadmeVersion;
import com.readmeeditor.model.Tag;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ReadmeRepository} using Testcontainers
 * with a real Redis instance.
 * <p>
 * These tests validate the full Redis persistence layer including
 * document CRUD, versioning, and tag management.
 */
@Testcontainers(disabledWithoutDocker = true)
class ReadmeRepositoryIntegrationTest {

    @Container
    private static final GenericContainer<?> redisContainer = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static RedisConfig redisConfig;
    private ReadmeRepository repository;
    private ObjectMapper objectMapper;

    @BeforeAll
    static void setupRedisConfig() {
        // Override Redis config to point to the Testcontainers Redis instance
        redisContainer.start();
        System.setProperty("redis.host", redisContainer.getHost());
        System.setProperty("redis.port", String.valueOf(redisContainer.getMappedPort(6379)));
        System.setProperty("redis.password", "");
        System.setProperty("redis.timeout", "3000");
        System.setProperty("redis.database", "1"); // Use a separate database for tests

        // Force reinitialization of RedisConfig singleton
        redisConfig = RedisConfig.getInstance();
    }

    @AfterAll
    static void cleanup() {
        redisContainer.stop();
        // Clean up system properties to prevent affecting other tests
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
        repository = new ReadmeRepository(redisConfig, objectMapper);

        // Clear test data
        try (var jedis = redisConfig.getConnection()) {
            jedis.flushDB();
        }
    }

    // ==================== Document CRUD Tests ====================

    @Test
    void saveDocument_shouldPersistAndRetrieveDocument() {
        // Arrange
        ReadmeDocument doc = new ReadmeDocument(
                UUID.randomUUID().toString(),
                "Test Document",
                "# Hello World\n\nThis is a test.",
                "user1"
        );

        // Act
        repository.saveDocument(doc);
        Optional<ReadmeDocument> retrieved = repository.findDocumentById(doc.getId());

        // Assert
        assertTrue(retrieved.isPresent());
        assertEquals(doc.getTitle(), retrieved.get().getTitle());
        assertEquals(doc.getContent(), retrieved.get().getContent());
        assertEquals(doc.getCreatedByUserId(), retrieved.get().getCreatedByUserId());
    }

    @Test
    void saveDocument_shouldUpdateExistingDocument() {
        // Arrange
        ReadmeDocument doc = new ReadmeDocument(
                UUID.randomUUID().toString(), "Original Title", "Original content", "user1");
        repository.saveDocument(doc);

        // Act
        doc.setTitle("Updated Title");
        doc.setContent("Updated content");
        repository.saveDocument(doc);

        // Assert
        Optional<ReadmeDocument> retrieved = repository.findDocumentById(doc.getId());
        assertTrue(retrieved.isPresent());
        assertEquals("Updated Title", retrieved.get().getTitle());
        assertEquals("Updated content", retrieved.get().getContent());
    }

    @Test
    void findDocumentById_withNonExistentId_shouldReturnEmpty() {
        // Act
        Optional<ReadmeDocument> result = repository.findDocumentById("nonexistent-id");

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void findAllDocuments_shouldReturnAllDocuments() {
        // Arrange
        repository.saveDocument(new ReadmeDocument("1", "Doc A", "Content A", "user1"));
        repository.saveDocument(new ReadmeDocument("2", "Doc B", "Content B", "user1"));
        repository.saveDocument(new ReadmeDocument("3", "Doc C", "Content C", "user2"));

        // Act
        List<ReadmeDocument> allDocs = repository.findAllDocuments();

        // Assert
        assertEquals(3, allDocs.size());
    }

    @Test
    void deleteDocument_shouldRemoveDocumentAndVersions() {
        // Arrange
        ReadmeDocument doc = new ReadmeDocument(
                UUID.randomUUID().toString(), "To Delete", "Content", "user1");
        repository.saveDocument(doc);
        assertEquals(1, repository.findAllDocuments().size());

        // Act
        repository.deleteDocument(doc.getId());

        // Assert
        assertEquals(0, repository.findAllDocuments().size());
        assertTrue(repository.findDocumentById(doc.getId()).isEmpty());
    }

    @Test
    void countDocuments_shouldReturnCorrectCount() {
        // Arrange
        repository.saveDocument(new ReadmeDocument("1", "Doc A", "", "user1"));
        repository.saveDocument(new ReadmeDocument("2", "Doc B", "", "user2"));

        // Act & Assert
        assertEquals(2, repository.countDocuments());
    }

    // ==================== Version Tests ====================

    @Test
    void saveVersion_shouldPersistVersion() {
        // Arrange
        ReadmeDocument doc = new ReadmeDocument(
                UUID.randomUUID().toString(), "Versioned Doc", "Content v1", "user1");
        repository.saveDocument(doc);

        ReadmeVersion version = new ReadmeVersion(
                UUID.randomUUID().toString(), doc.getId(), 1,
                "Content v1", "user1", "Initial version");

        // Act
        repository.saveVersion(version);
        List<ReadmeVersion> versions = repository.getVersions(doc.getId());

        // Assert
        assertEquals(1, versions.size());
        assertEquals(1, versions.get(0).getVersionNumber());
        assertEquals("Initial version", versions.get(0).getCommitMessage());
    }

    @Test
    void getVersions_shouldReturnMultipleVersionsOrderedByNumber() {
        // Arrange
        String docId = UUID.randomUUID().toString();
        ReadmeDocument doc = new ReadmeDocument(docId, "Multi Version Doc", "v1", "user1");
        repository.saveDocument(doc);

        ReadmeVersion v1 = new ReadmeVersion("v1", docId, 1, "Content v1", "user1", "First");
        ReadmeVersion v2 = new ReadmeVersion("v2", docId, 2, "Content v2", "user2", "Second");
        ReadmeVersion v3 = new ReadmeVersion("v3", docId, 3, "Content v3", "user1", "Third");

        repository.saveVersion(v1);
        repository.saveVersion(v2);
        repository.saveVersion(v3);

        // Act
        List<ReadmeVersion> versions = repository.getVersions(docId);

        // Assert
        assertEquals(3, versions.size());
        assertEquals(3, versions.get(0).getVersionNumber()); // Newest first
        assertEquals(2, versions.get(1).getVersionNumber());
        assertEquals(1, versions.get(2).getVersionNumber());
    }

    @Test
    void getVersion_shouldReturnSpecificVersion() {
        // Arrange
        String docId = UUID.randomUUID().toString();
        repository.saveDocument(new ReadmeDocument(docId, "Doc", "Content", "user1"));

        ReadmeVersion v2 = new ReadmeVersion("v2", docId, 2, "Content v2", "user1", "Update");
        repository.saveVersion(new ReadmeVersion("v1", docId, 1, "Content v1", "user1", "Initial"));
        repository.saveVersion(v2);

        // Act
        Optional<ReadmeVersion> result = repository.getVersion(docId, 2);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("Content v2", result.get().getContent());
        assertEquals("Update", result.get().getCommitMessage());
    }

    // ==================== Tag Tests ====================

    @Test
    void saveTag_shouldPersistTag() {
        // Arrange
        Tag tag = new Tag(UUID.randomUUID().toString(), "documentation");

        // Act
        repository.saveTag(tag);
        Optional<Tag> retrieved = repository.findTagById(tag.getId());

        // Assert
        assertTrue(retrieved.isPresent());
        assertEquals("documentation", retrieved.get().getName());
    }

    @Test
    void findTagByName_shouldReturnTag() {
        // Arrange
        Tag tag = new Tag(UUID.randomUUID().toString(), "java");
        repository.saveTag(tag);

        // Act
        Optional<Tag> retrieved = repository.findTagByName("java");

        // Assert
        assertTrue(retrieved.isPresent());
        assertEquals(tag.getId(), retrieved.get().getId());
    }

    @Test
    void findTagByName_shouldBeCaseInsensitive() {
        // Arrange
        Tag tag = new Tag(UUID.randomUUID().toString(), "SpringBoot");
        repository.saveTag(tag);

        // Act
        Optional<Tag> retrieved = repository.findTagByName("springboot");

        // Assert
        assertTrue(retrieved.isPresent());
    }

    @Test
    void addTagToDocument_shouldAssociateTag() {
        // Arrange
        ReadmeDocument doc = new ReadmeDocument("doc1", "Tagged Doc", "Content", "user1");
        repository.saveDocument(doc);
        Tag tag = new Tag("tag1", "important");
        repository.saveTag(tag);

        // Act
        repository.addTagToDocument(doc.getId(), tag.getId());

        // Assert
        Optional<ReadmeDocument> retrieved = repository.findDocumentById(doc.getId());
        assertTrue(retrieved.isPresent());
        assertTrue(retrieved.get().getTagIds().contains(tag.getId()));
    }

    @Test
    void findDocumentsByTagName_shouldReturnTaggedDocuments() {
        // Arrange
        Tag tag = new Tag("tag1", "release");
        repository.saveTag(tag);

        ReadmeDocument doc1 = new ReadmeDocument("d1", "Doc 1", "", "user1");
        ReadmeDocument doc2 = new ReadmeDocument("d2", "Doc 2", "", "user1");
        repository.saveDocument(doc1);
        repository.saveDocument(doc2);
        repository.addTagToDocument("d1", "tag1");
        repository.addTagToDocument("d2", "tag1");

        // Act
        List<ReadmeDocument> tagged = repository.findDocumentsByTagName("release");

        // Assert
        assertEquals(2, tagged.size());
    }

    @Test
    void deleteTag_shouldRemoveFromAllDocuments() {
        // Arrange
        Tag tag = new Tag("tag1", "obsolete");
        repository.saveTag(tag);

        ReadmeDocument doc = new ReadmeDocument("d1", "Doc", "", "user1");
        repository.saveDocument(doc);
        repository.addTagToDocument("d1", "tag1");

        // Act
        repository.deleteTag("tag1");

        // Assert
        assertTrue(repository.findTagById("tag1").isEmpty());
        Optional<ReadmeDocument> retrieved = repository.findDocumentById("d1");
        assertTrue(retrieved.isPresent());
        assertFalse(retrieved.get().getTagIds().contains("tag1"));
    }

    // ==================== Search Tests ====================

    @Test
    void searchDocuments_shouldFindByTitle() {
        // Arrange
        repository.saveDocument(new ReadmeDocument("1", "Spring Boot Guide", "Content", "user1"));
        repository.saveDocument(new ReadmeDocument("2", "Java Tutorial", "Content", "user1"));

        // Act
        List<ReadmeDocument> results = repository.searchDocuments("Spring");

        // Assert
        assertEquals(1, results.size());
        assertEquals("Spring Boot Guide", results.get(0).getTitle());
    }

    @Test
    void searchDocuments_shouldFindByContent() {
        // Arrange
        repository.saveDocument(new ReadmeDocument("1", "Doc A", "This document covers API integration", "user1"));
        repository.saveDocument(new ReadmeDocument("2", "Doc B", "Just some notes", "user1"));

        // Act
        List<ReadmeDocument> results = repository.searchDocuments("API integration");

        // Assert
        assertEquals(1, results.size());
        assertEquals("Doc A", results.get(0).getTitle());
    }
}
