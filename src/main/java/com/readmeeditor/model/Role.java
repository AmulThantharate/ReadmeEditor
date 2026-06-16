package com.readmeeditor.model;

/**
 * Enum representing user roles for role-based access control.
 * <p>
 * ADMIN - Full access to all features including user management.
 * USER  - Standard access to README creation, editing, and management.
 */
public enum Role {
    ADMIN("Admin"),
    USER("User");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parses a role from its display name.
     *
     * @param displayName the display name to parse
     * @return the matching Role
     * @throws IllegalArgumentException if no matching role is found
     */
    public static Role fromDisplayName(String displayName) {
        for (Role role : values()) {
            if (role.displayName.equalsIgnoreCase(displayName)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + displayName);
    }
}
