package com.readmeeditor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Central configuration class that loads settings from {@code application.properties}.
 * <p>
 * Provides typed access to all application configuration values.
 */
public class AppConfig {
    private static final Logger LOG = LoggerFactory.getLogger(AppConfig.class);
    private static final String CONFIG_FILE = "application.properties";

    private final Properties properties;

    private static AppConfig instance;

    /**
     * Returns the singleton {@code AppConfig} instance, loading
     * configuration from {@code application.properties} on first call.
     */
    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    private AppConfig() {
        properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                LOG.warn("Configuration file '{}' not found on classpath; using defaults", CONFIG_FILE);
                return;
            }
            properties.load(input);
            LOG.info("Configuration loaded successfully from '{}'", CONFIG_FILE);
        } catch (IOException e) {
            LOG.error("Failed to load configuration file '{}'", CONFIG_FILE, e);
        }
    }

    // --- Redis Configuration ---

    public String getRedisHost() {
        String sysProp = System.getProperty("redis.host");
        if (sysProp != null) return sysProp;
        String envVar = System.getenv("REDIS_HOST");
        if (envVar != null) return envVar;
        return properties.getProperty("redis.host", "localhost");
    }

    public int getRedisPort() {
        String sysProp = System.getProperty("redis.port");
        if (sysProp != null) return Integer.parseInt(sysProp);
        String envVar = System.getenv("REDIS_PORT");
        if (envVar != null) return Integer.parseInt(envVar);
        return Integer.parseInt(properties.getProperty("redis.port", "6379"));
    }

    public String getRedisPassword() {
        String sysProp = System.getProperty("redis.password");
        if (sysProp != null) return sysProp;
        String envVar = System.getenv("REDIS_PASSWORD");
        if (envVar != null) return envVar;
        return properties.getProperty("redis.password", "");
    }

    public int getRedisTimeout() {
        return Integer.parseInt(properties.getProperty("redis.timeout", "2000"));
    }

    public int getRedisDatabase() {
        String sysProp = System.getProperty("redis.database");
        if (sysProp != null) return Integer.parseInt(sysProp);
        String envVar = System.getenv("REDIS_DATABASE");
        if (envVar != null) return Integer.parseInt(envVar);
        return Integer.parseInt(properties.getProperty("redis.database", "0"));
    }

    // --- Application Settings ---

    public String getAppName() {
        return properties.getProperty("app.name", "README Editor");
    }

    public String getAppVersion() {
        return properties.getProperty("app.version", "1.0.0");
    }

    public String getAppDataDir() {
        return properties.getProperty("app.data.dir", System.getProperty("user.home") + "/.readme-editor");
    }

    // --- Auto-Save Configuration ---

    public boolean isAutoSaveEnabled() {
        return Boolean.parseBoolean(properties.getProperty("autosave.enabled", "true"));
    }

    public int getAutoSaveIntervalMs() {
        return Integer.parseInt(properties.getProperty("autosave.interval.ms", "5000"));
    }

    // --- Editor Defaults ---

    public String getDefaultTheme() {
        return properties.getProperty("editor.default.theme", "light");
    }

    public int getDefaultFontSize() {
        return Integer.parseInt(properties.getProperty("editor.default.font.size", "14"));
    }

    public int getDefaultTabSize() {
        return Integer.parseInt(properties.getProperty("editor.tab.size", "2"));
    }

    // --- Security ---

    public int getPasswordMinLength() {
        return Integer.parseInt(properties.getProperty("security.password.min.length", "8"));
    }

    public int getBcryptRounds() {
        return Integer.parseInt(properties.getProperty("security.bcrypt.rounds", "12"));
    }

    public int getSessionTimeoutMinutes() {
        return Integer.parseInt(properties.getProperty("security.session.timeout.minutes", "60"));
    }

    // --- Web Server Configuration ---

    public int getServerPort() {
        String sysProp = System.getProperty("server.port");
        if (sysProp != null) return Integer.parseInt(sysProp);
        String envVar = System.getenv("PORT");
        if (envVar != null) return Integer.parseInt(envVar);
        String envVar2 = System.getenv("SERVER_PORT");
        if (envVar2 != null) return Integer.parseInt(envVar2);
        return Integer.parseInt(properties.getProperty("server.port", "8080"));
    }
}
