package com.readmeeditor.repository;

import com.readmeeditor.config.RedisConfig;
import com.readmeeditor.model.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.util.*;

/**
 * Repository for managing user settings in Redis.
 * <p>
 * Storage schema:
 * <ul>
 *   <li>{@code settings:{userId}} — Hash containing settings fields</li>
 * </ul>
 */
public class SettingsRepository {
    private static final Logger LOG = LoggerFactory.getLogger(SettingsRepository.class);
    private static final String KEY_PREFIX = "settings:";

    private final RedisConfig redisConfig;

    public SettingsRepository() {
        this.redisConfig = RedisConfig.getInstance();
    }

    SettingsRepository(RedisConfig redisConfig) {
        this.redisConfig = redisConfig;
    }

    /**
     * Saves user settings to Redis.
     *
     * @param settings the settings to save
     * @return the saved settings
     */
    public Settings save(Settings settings) {
        Objects.requireNonNull(settings, "Settings must not be null");
        Objects.requireNonNull(settings.getUserId(), "User ID must not be null");

        try (Jedis jedis = redisConfig.getConnection()) {
            String key = KEY_PREFIX + settings.getUserId();
            Map<String, String> hash = new HashMap<>();
            hash.put("theme", settings.getTheme());
            hash.put("autoSaveIntervalMs", String.valueOf(settings.getAutoSaveIntervalMs()));
            hash.put("editorFontSize", String.valueOf(settings.getEditorFontSize()));
            hash.put("tabSize", String.valueOf(settings.getTabSize()));
            hash.put("spellCheckEnabled", String.valueOf(settings.isSpellCheckEnabled()));

            jedis.hset(key, hash);
            LOG.debug("Settings saved for user: {}", settings.getUserId());
            return settings;
        }
    }

    /**
     * Finds settings for a specific user.
     *
     * @param userId the user ID
     * @return an Optional containing the settings, or empty if not found
     */
    public Optional<Settings> findByUserId(String userId) {
        Objects.requireNonNull(userId, "User ID must not be null");

        try (Jedis jedis = redisConfig.getConnection()) {
            String key = KEY_PREFIX + userId;
            Map<String, String> hash = jedis.hgetAll(key);
            if (hash.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(mapToSettings(userId, hash));
        }
    }

    /**
     * Gets settings for a user, returning defaults if none are stored.
     *
     * @param userId the user ID
     * @return the settings (never null)
     */
    public Settings getOrDefault(String userId) {
        return findByUserId(userId).orElseGet(() -> {
            Settings defaults = new Settings(userId);
            save(defaults);
            return defaults;
        });
    }

    /**
     * Deletes settings for a user.
     */
    public void deleteByUserId(String userId) {
        try (Jedis jedis = redisConfig.getConnection()) {
            jedis.del(KEY_PREFIX + userId);
            LOG.debug("Settings deleted for user: {}", userId);
        }
    }

    /**
     * Maps a Redis hash to a Settings object.
     */
    private Settings mapToSettings(String userId, Map<String, String> hash) {
        Settings settings = new Settings(userId);
        settings.setTheme(hash.getOrDefault("theme", "light"));
        settings.setAutoSaveIntervalMs(Integer.parseInt(hash.getOrDefault("autoSaveIntervalMs", "5000")));
        settings.setEditorFontSize(Integer.parseInt(hash.getOrDefault("editorFontSize", "14")));
        settings.setTabSize(Integer.parseInt(hash.getOrDefault("tabSize", "2")));
        settings.setSpellCheckEnabled(Boolean.parseBoolean(hash.getOrDefault("spellCheckEnabled", "false")));
        return settings;
    }
}
