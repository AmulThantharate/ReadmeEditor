package com.readmeeditor.service;

import com.readmeeditor.model.ReadmeDocument;
import com.readmeeditor.model.ReadmeVersion;
import com.readmeeditor.model.Workspace;
import com.readmeeditor.repository.ReadmeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for README document management.
 * <p>
 * Handles document creation, updating, deletion, and querying operations.
 * Coordinates with templates and versioning as needed.
 */
public class ReadmeService {
    private static final Logger LOG = LoggerFactory.getLogger(ReadmeService.class);

    private final ReadmeRepository readmeRepository;
    private final VersionService versionService;

    public ReadmeService() {
        this.readmeRepository = new ReadmeRepository();
        this.versionService = new VersionService(readmeRepository);
    }

    public ReadmeService(ReadmeRepository readmeRepository, VersionService versionService) {
        this.readmeRepository = readmeRepository;
        this.versionService = versionService;
    }

    /**
     * Creates a new README document with auto-generated ID.
     *
     * @param title    the document title
     * @param content  the Markdown content
     * @param userId   the ID of the creating user
     * @return the created document
     */
    public ReadmeDocument createDocument(String title, String content, String userId) {
        List<Workspace> workspaces = findWorkspacesByUserId(userId);
        String workspaceId = workspaces.isEmpty() ? "" : workspaces.get(0).getId();
        return createDocument(title, content, userId, workspaceId);
    }

    /**
     * Creates a new README document inside a specific workspace.
     */
    public ReadmeDocument createDocument(String title, String content, String userId, String workspaceId) {
        ReadmeDocument doc = new ReadmeDocument(
                UUID.randomUUID().toString(),
                title != null ? title : "Untitled README",
                content != null ? content : "",
                userId
        );
        doc.setWorkspaceId(workspaceId);
        ReadmeDocument saved = readmeRepository.saveDocument(doc);

        // Create initial version
        ReadmeVersion initialVersion = new ReadmeVersion(
                UUID.randomUUID().toString(),
                saved.getId(),
                0,
                saved.getContent(),
                userId,
                "Initial version"
        );
        versionService.saveVersion(initialVersion);

        LOG.info("Created document: {} (ID: {}) in workspace: {}", saved.getTitle(), saved.getId(), workspaceId);
        return saved;
    }

    // ==================== Workspace Management ====================

    /**
     * Creates a new workspace.
     */
    public Workspace createWorkspace(String name, String userId) {
        Workspace ws = new Workspace(UUID.randomUUID().toString(), name, userId);
        return readmeRepository.saveWorkspace(ws);
    }

    /**
     * Finds all workspaces belonging to a user.
     */
    public List<Workspace> findWorkspacesByUserId(String userId) {
        return readmeRepository.findWorkspacesByUserId(userId);
    }

    /**
     * Finds a workspace by its ID.
     */
    public Optional<Workspace> findWorkspaceById(String id) {
        return readmeRepository.findWorkspaceById(id);
    }

    /**
     * Deletes a workspace and its documents.
     */
    public void deleteWorkspace(String workspaceId) {
        readmeRepository.deleteWorkspace(workspaceId);
    }

    /**
     * Finds documents belonging to a workspace.
     */
    public List<ReadmeDocument> findDocumentsByWorkspaceId(String workspaceId) {
        return readmeRepository.findDocumentsByWorkspaceId(workspaceId);
    }

    /**
     * Saves an existing document and creates a new version if content changed.
     *
     * @param doc the document to save (must have an ID)
     * @return the saved document
     */
    public ReadmeDocument saveDocument(ReadmeDocument doc) {
        if (doc.getId() == null) {
            throw new IllegalArgumentException("Cannot save a document without an ID. Use createDocument() instead.");
        }

        ReadmeDocument saved = readmeRepository.saveDocument(doc);
        LOG.debug("Document saved: {} (v{})", saved.getTitle(), saved.getCurrentVersion());
        return saved;
    }

    /**
     * Saves a document and creates a new version entry.
     *
     * @param doc           the document to save
     * @param userId        the user making the change
     * @param commitMessage a message describing the change
     * @return the saved document with incremented version
     */
    public ReadmeDocument saveWithVersion(ReadmeDocument doc, String userId, String commitMessage) {
        doc.incrementVersion();
        ReadmeDocument saved = readmeRepository.saveDocument(doc);

        ReadmeVersion version = new ReadmeVersion(
                UUID.randomUUID().toString(),
                saved.getId(),
                saved.getCurrentVersion(),
                saved.getContent(),
                userId,
                commitMessage != null ? commitMessage : "Updated README"
        );
        versionService.saveVersion(version);

        LOG.info("Document saved with new version: {} (v{})", saved.getTitle(), saved.getCurrentVersion());
        return saved;
    }

    /**
     * Finds a document by its ID.
     */
    public Optional<ReadmeDocument> findDocumentById(String id) {
        return readmeRepository.findDocumentById(id);
    }

    /**
     * Returns all documents.
     */
    public List<ReadmeDocument> findAllDocuments() {
        return readmeRepository.findAllDocuments();
    }

    /**
     * Returns documents created by a specific user.
     */
    public List<ReadmeDocument> findDocumentsByUserId(String userId) {
        return readmeRepository.findDocumentsByUserId(userId);
    }

    /**
     * Returns the most recent documents.
     */
    public List<ReadmeDocument> findRecentDocuments(int limit) {
        return readmeRepository.findRecentDocuments(limit);
    }

    /**
     * Searches documents by title and content.
     */
    public List<ReadmeDocument> searchDocuments(String query) {
        return readmeRepository.searchDocuments(query);
    }

    /**
     * Deletes a document and its version history.
     */
    public void deleteDocument(String id) {
        readmeRepository.deleteDocument(id);
    }

    /**
     * Returns the total document count.
     */
    public long countDocuments() {
        return readmeRepository.countDocuments();
    }

    /**
     * Returns the total number of versions across all documents.
     */
    public long countTotalVersions() {
        return readmeRepository.countTotalVersions();
    }
}
