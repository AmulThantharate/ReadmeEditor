package com.readmeeditor.model;

import java.util.Objects;

/**
 * Model class representing a tag that can be attached to README documents.
 * <p>
 * Stored in Redis as a string under the key {@code tags:{id}}.
 * Name lookup is supported via {@code tags:name:{name}}.
 */
public class Tag {
    private String id;
    private String name;

    public Tag() {}

    public Tag(String id, String name) {
        this.id = id;
        this.name = name;
    }

    // --- Getters and Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tag tag = (Tag) o;
        return Objects.equals(id, tag.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Tag{id='" + id + "', name='" + name + "'}";
    }
}
