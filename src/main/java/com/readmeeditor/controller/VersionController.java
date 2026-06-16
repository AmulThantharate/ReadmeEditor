package com.readmeeditor.controller;

import com.readmeeditor.model.ReadmeDocument;
import com.readmeeditor.model.ReadmeVersion;
import com.readmeeditor.service.ReadmeService;
import com.readmeeditor.service.VersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Controller for version management operations from the UI.
 * <p>
 * Handles viewing version history, restoring previous versions,
 * and comparing versions side-by-side.
 */
public class VersionController {
    private static final Logger LOG = LoggerFactory.getLogger(VersionController.class);

    private final VersionService versionService;
    private final ReadmeService readmeService;

    public VersionController() {
        this.versionService = new VersionService(new com.readmeeditor.repository.ReadmeRepository());
        this.readmeService = new ReadmeService();
    }

    public VersionController(VersionService versionService, ReadmeService readmeService) {
        this.versionService = versionService;
        this.readmeService = readmeService;
    }

    /**
     * Returns the version history for a document.
     *
     * @param documentId the document ID
     * @return list of versions, newest first
     */
    public List<ReadmeVersion> getVersionHistory(String documentId) {
        return versionService.getVersions(documentId);
    }

    /**
     * Returns a specific version.
     */
    public Optional<ReadmeVersion> getVersion(String documentId, int versionNumber) {
        return versionService.getVersion(documentId, versionNumber);
    }

    /**
     * Restores a document to a previous version.
     *
     * @param documentId    the document ID
     * @param versionNumber the version to restore
     * @param userId        the user performing the restore
     * @return the restored document
     */
    public ReadmeDocument restoreVersion(String documentId, int versionNumber, String userId) {
        LOG.info("Restoring document {} to version {}", documentId, versionNumber);
        return versionService.restoreVersion(documentId, versionNumber, userId);
    }

    /**
     * Compares two versions and returns a diff string.
     *
     * @param documentId      the document ID
     * @param oldVersionNumber the older version
     * @param newVersionNumber the newer version
     * @return a formatted diff
     */
    public String compareVersions(String documentId, int oldVersionNumber, int newVersionNumber) {
        return versionService.compareVersions(documentId, oldVersionNumber, newVersionNumber);
    }

    /**
     * Returns the version count for a document.
     */
    public int getVersionCount(String documentId) {
        return versionService.getVersionCount(documentId);
    }

    /**
     * Gets the document title for display in the version history.
     */
    public String getDocumentTitle(String documentId) {
        return readmeService.findDocumentById(documentId)
                .map(ReadmeDocument::getTitle)
                .orElse("Unknown Document");
    }
}
