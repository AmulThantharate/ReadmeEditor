package com.readmeeditor;

/**
 * Launcher class to bypass JavaFX runtime components verification when launching from a shaded JAR.
 */
public class Launcher {
    public static void main(String[] args) {
        ReadmeEditorApplication.main(args);
    }
}
