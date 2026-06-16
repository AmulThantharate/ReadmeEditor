package com.readmeeditor.service;

import com.readmeeditor.model.ReadmeDocument;
import com.readmeeditor.model.ReadmeVersion;
import com.readmeeditor.repository.ReadmeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing document versions.
 * <p>
 * Supports saving versions, viewing history, restoring previous versions,
 * and comparing two versions to see what changed.
 */
public class VersionService {
    private static final Logger LOG = LoggerFactory.getLogger(VersionService.class);

    private final ReadmeRepository readmeRepository;

    public VersionService(ReadmeRepository readmeRepository) {
        this.readmeRepository = readmeRepository;
    }

    /**
     * Saves a new version entry.
     */
    public void saveVersion(ReadmeVersion version) {
        readmeRepository.saveVersion(version);
    }

    /**
     * Returns all versions of a document, newest first.
     */
    public List<ReadmeVersion> getVersions(String documentId) {
        return readmeRepository.getVersions(documentId);
    }

    /**
     * Returns a specific version.
     */
    public Optional<ReadmeVersion> getVersion(String documentId, int versionNumber) {
        return readmeRepository.getVersion(documentId, versionNumber);
    }

    /**
     * Restores a document to a previous version.
     *
     * @param documentId    the document ID
     * @param versionNumber the version to restore
     * @param userId        the user performing the restore
     * @return the restored document (with incremented version)
     */
    public ReadmeDocument restoreVersion(String documentId, int versionNumber, String userId) {
        Optional<ReadmeDocument> docOpt = readmeRepository.findDocumentById(documentId);
        Optional<ReadmeVersion> versionOpt = getVersion(documentId, versionNumber);

        if (docOpt.isEmpty()) {
            throw new IllegalArgumentException("Document not found: " + documentId);
        }
        if (versionOpt.isEmpty()) {
            throw new IllegalArgumentException("Version not found: " + versionNumber);
        }

        ReadmeDocument doc = docOpt.get();
        ReadmeVersion version = versionOpt.get();

        // Restore the content from the old version
        doc.setContent(version.getContent());
        doc.incrementVersion();
        ReadmeDocument saved = readmeRepository.saveDocument(doc);

        // Create a new version entry marking the restore
        ReadmeVersion restoredVersion = new ReadmeVersion(
                java.util.UUID.randomUUID().toString(),
                saved.getId(),
                saved.getCurrentVersion(),
                saved.getContent(),
                userId,
                "Restored from version " + versionNumber
        );
        readmeRepository.saveVersion(restoredVersion);

        LOG.info("Document {} restored to version {} (new v{})", documentId, versionNumber, saved.getCurrentVersion());
        return saved;
    }

    /**
     * Compares two versions of the same document and returns a diff.
     * <p>
     * Uses a simple line-based diff approach.
     *
     * @param documentId      the document ID
     * @param oldVersionNumber the older version
     * @param newVersionNumber the newer version
     * @return a formatted diff string, or empty if versions are identical
     */
    public String compareVersions(String documentId, int oldVersionNumber, int newVersionNumber) {
        Optional<ReadmeVersion> oldVersionOpt = getVersion(documentId, oldVersionNumber);
        Optional<ReadmeVersion> newVersionOpt = getVersion(documentId, newVersionNumber);

        if (oldVersionOpt.isEmpty()) {
            throw new IllegalArgumentException("Old version not found: " + oldVersionNumber);
        }
        if (newVersionOpt.isEmpty()) {
            throw new IllegalArgumentException("New version not found: " + newVersionNumber);
        }

        String oldContent = oldVersionOpt.get().getContent() != null ? oldVersionOpt.get().getContent() : "";
        String newContent = newVersionOpt.get().getContent() != null ? newVersionOpt.get().getContent() : "";

        if (oldContent.equals(newContent)) {
            return "No differences between versions " + oldVersionNumber + " and " + newVersionNumber + ".";
        }

        StringBuilder diff = new StringBuilder();
        String[] oldLines = oldContent.split("\n");
        String[] newLines = newContent.split("\n");

        diff.append("--- Version ").append(oldVersionNumber).append("\n");
        diff.append("+++ Version ").append(newVersionNumber).append("\n\n");

        int maxLine = Math.max(oldLines.length, newLines.length);
        for (int i = 0; i < maxLine; i++) {
            String oldLine = i < oldLines.length ? oldLines[i] : "";
            String newLine = i < newLines.length ? newLines[i] : "";

            if (!oldLine.equals(newLine)) {
                diff.append("Line ").append(i + 1).append(":\n");
                if (i < oldLines.length) {
                    diff.append("- ").append(oldLine).append("\n");
                }
                if (i < newLines.length) {
                    diff.append("+ ").append(newLine).append("\n");
                }
                diff.append("\n");
            }
        }

        return diff.toString().trim();
    }

    /**
     * Returns the version count for a document.
     */
    public int getVersionCount(String documentId) {
        return getVersions(documentId).size();
    }
}
