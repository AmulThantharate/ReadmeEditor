package com.readmeeditor.util;

import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Manages application theme switching between dark and light modes.
 * <p>
 * Themes are applied via CSS stylesheets loaded from the classpath.
 */
public final class ThemeManager {
    private static final Logger LOG = LoggerFactory.getLogger(ThemeManager.class);

    private static final String DARK_THEME = "/css/dark-theme.css";
    private static final String LIGHT_THEME = "/css/light-theme.css";
    private static final String MAIN_CSS = "/css/main.css";

    private static ThemeManager instance;
    private String currentTheme;
    private final Set<Scene> registeredScenes = new HashSet<>();

    private ThemeManager() {
        this.currentTheme = "light";
    }

    /**
     * Returns the singleton {@code ThemeManager} instance.
     */
    public static synchronized ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    /**
     * Registers a scene so that theme changes are automatically applied to it.
     *
     * @param scene the JavaFX scene to register
     */
    public void registerScene(Scene scene) {
        Objects.requireNonNull(scene, "Scene must not be null");
        registeredScenes.add(scene);
        applyThemeToScene(scene, currentTheme);
        LOG.debug("Scene registered for theme management: {}", scene);
    }

    /**
     * Unregisters a scene from automatic theme updates.
     *
     * @param scene the JavaFX scene to unregister
     */
    public void unregisterScene(Scene scene) {
        registeredScenes.remove(scene);
        LOG.debug("Scene unregistered from theme management");
    }

    /**
     * Sets the current theme and applies it to all registered scenes.
     *
     * @param theme the theme name ("light" or "dark")
     * @throws IllegalArgumentException if the theme name is not supported
     */
    public void setTheme(String theme) {
        if (!"light".equals(theme) && !"dark".equals(theme)) {
            throw new IllegalArgumentException("Unsupported theme: " + theme
                    + ". Supported themes: 'light', 'dark'");
        }
        this.currentTheme = theme;
        for (Scene scene : registeredScenes) {
            applyThemeToScene(scene, theme);
        }
        LOG.info("Theme switched to '{}' (applied to {} scenes)", theme, registeredScenes.size());
    }

    /**
     * Returns the current theme name.
     *
     * @return "light" or "dark"
     */
    public String getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Toggles between light and dark themes.
     *
     * @return the new theme name
     */
    public String toggleTheme() {
        String newTheme = "light".equals(currentTheme) ? "dark" : "light";
        setTheme(newTheme);
        return newTheme;
    }

    /**
     * Applies the given theme to a specific scene.
     */
    private void applyThemeToScene(Scene scene, String theme) {
        scene.getStylesheets().clear();
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource(MAIN_CSS)).toExternalForm());

        String themeCss = "light".equals(theme) ? LIGHT_THEME : DARK_THEME;
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource(themeCss)).toExternalForm());
    }
}
