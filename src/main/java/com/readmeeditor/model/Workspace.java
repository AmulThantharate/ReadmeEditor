package com.readmeeditor.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Model class representing a Workspace.
 */
public class Workspace {
    private String id;
    private String name;
    private String createdByUserId;
    private LocalDateTime createdAt;

    public Workspace() {
        this.createdAt = LocalDateTime.now();
    }

    public Workspace(String id, String name, String createdByUserId) {
        this();
        this.id = id;
        this.name = name;
        this.createdByUserId = createdByUserId;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(String createdByUserId) { this.createdByUserId = createdByUserId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Workspace workspace = (Workspace) o;
        return Objects.equals(id, workspace.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Workspace{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", createdByUserId='" + createdByUserId + '\'' +
                '}';
    }
}
