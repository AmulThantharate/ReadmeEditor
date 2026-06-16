package com.readmeeditor.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Model class representing a README document.
 * <p>
 * Stored in Redis as a hash under the key {@code readme:{id}}.
 * Versions are stored separately under {@code readme:{id}:versions}.
 */
public class ReadmeDocument {
    private String id;
    private String title;
    private String content;
    private List<String> tagIds;
    private String createdByUserId;
    private String workspaceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int currentVersion;

    public ReadmeDocument() {
        this.tagIds = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.currentVersion = 0;
    }

    public ReadmeDocument(String id, String title, String content, String createdByUserId) {
        this();
        this.id = id;
        this.title = title;
        this.content = content;
        this.createdByUserId = createdByUserId;
    }

    // --- Getters and Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<String> getTagIds() { return tagIds; }
    public void setTagIds(List<String> tagIds) { this.tagIds = tagIds; }

    public void addTagId(String tagId) {
        if (this.tagIds == null) {
            this.tagIds = new ArrayList<>();
        }
        if (!this.tagIds.contains(tagId)) {
            this.tagIds.add(tagId);
        }
    }

    public void removeTagId(String tagId) {
        if (this.tagIds != null) {
            this.tagIds.remove(tagId);
        }
    }

    public String getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(String createdByUserId) { this.createdByUserId = createdByUserId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public int getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(int currentVersion) { this.currentVersion = currentVersion; }

    public void incrementVersion() {
        this.currentVersion++;
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReadmeDocument that = (ReadmeDocument) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ReadmeDocument{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", version=" + currentVersion +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
