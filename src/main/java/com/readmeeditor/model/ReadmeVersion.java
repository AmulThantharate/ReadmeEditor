package com.readmeeditor.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Model class representing a specific version of a README document.
 * <p>
 * Stored in Redis as a sorted set member under {@code readme:{documentId}:versions},
 * with the score being the version number.
 */
public class ReadmeVersion {
    private String id;
    private String documentId;
    private int versionNumber;
    private String content;
    private LocalDateTime createdAt;
    private String createdByUserId;
    private String commitMessage;

    public ReadmeVersion() {
        this.createdAt = LocalDateTime.now();
    }

    public ReadmeVersion(String id, String documentId, int versionNumber,
                         String content, String createdByUserId, String commitMessage) {
        this();
        this.id = id;
        this.documentId = documentId;
        this.versionNumber = versionNumber;
        this.content = content;
        this.createdByUserId = createdByUserId;
        this.commitMessage = commitMessage;
    }

    // --- Getters and Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(String createdByUserId) { this.createdByUserId = createdByUserId; }

    public String getCommitMessage() { return commitMessage; }
    public void setCommitMessage(String commitMessage) { this.commitMessage = commitMessage; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReadmeVersion that = (ReadmeVersion) o;
        return versionNumber == that.versionNumber && Objects.equals(documentId, that.documentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentId, versionNumber);
    }

    @Override
    public String toString() {
        return "ReadmeVersion{" +
                "documentId='" + documentId + '\'' +
                ", version=" + versionNumber +
                ", commitMessage='" + commitMessage + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
