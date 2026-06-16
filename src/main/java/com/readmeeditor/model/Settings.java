package com.readmeeditor.model;

import java.util.Objects;

/**
 * Model class representing user-specific application settings.
 * <p>
 * Stored in Redis as a hash under the key {@code settings:{userId}}.
 */
public class Settings {
    private String userId;
    private String theme;            // "light" or "dark"
    private int autoSaveIntervalMs;
    private int editorFontSize;
    private int tabSize;
    private boolean spellCheckEnabled;

    public Settings() {
        this.theme = "light";
        this.autoSaveIntervalMs = 5000;
        this.editorFontSize = 14;
        this.tabSize = 2;
        this.spellCheckEnabled = false;
    }

    public Settings(String userId) {
        this();
        this.userId = userId;
    }

    // --- Getters and Setters ---

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public int getAutoSaveIntervalMs() { return autoSaveIntervalMs; }
    public void setAutoSaveIntervalMs(int autoSaveIntervalMs) { this.autoSaveIntervalMs = autoSaveIntervalMs; }

    public int getEditorFontSize() { return editorFontSize; }
    public void setEditorFontSize(int editorFontSize) { this.editorFontSize = editorFontSize; }

    public int getTabSize() { return tabSize; }
    public void setTabSize(int tabSize) { this.tabSize = tabSize; }

    public boolean isSpellCheckEnabled() { return spellCheckEnabled; }
    public void setSpellCheckEnabled(boolean spellCheckEnabled) { this.spellCheckEnabled = spellCheckEnabled; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Settings settings = (Settings) o;
        return Objects.equals(userId, settings.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return "Settings{userId='" + userId + "', theme='" + theme + "'}";
    }
}
