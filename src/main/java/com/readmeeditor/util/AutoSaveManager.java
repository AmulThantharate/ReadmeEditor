package com.readmeeditor.util;

import com.readmeeditor.model.ReadmeDocument;
import com.readmeeditor.service.ReadmeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages auto-saving of README documents at a configurable interval.
 * <p>
 * Uses a scheduled executor to periodically persist the current document
 * state to Redis. Only saves when content has actually changed.
 */
public class AutoSaveManager implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(AutoSaveManager.class);

    private final ScheduledExecutorService scheduler;
    private final ReadmeService readmeService;
    private final AtomicReference<ReadmeDocument> currentDocument;
    private final AtomicReference<String> lastSavedContent;
    private final AtomicBoolean enabled;
    private ScheduledFuture<?> autoSaveTask;

    /**
     * Creates a new AutoSaveManager.
     *
     * @param readmeService the service to use for saving documents
     * @param intervalMs    the interval between auto-saves in milliseconds
     */
    public AutoSaveManager(ReadmeService readmeService, int intervalMs) {
        this.readmeService = readmeService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "autosave-thread");
            t.setDaemon(true);
            return t;
        });
        this.currentDocument = new AtomicReference<>(null);
        this.lastSavedContent = new AtomicReference<>("");
        this.enabled = new AtomicBoolean(true);
        startAutoSaveTask(intervalMs);
        LOG.info("Auto-save manager initialized with {}ms interval", intervalMs);
    }

    /**
     * Creates a new AutoSaveManager with the default interval from configuration.
     */
    public AutoSaveManager(ReadmeService readmeService) {
        this(readmeService, com.readmeeditor.config.AppConfig.getInstance().getAutoSaveIntervalMs());
    }

    /**
     * Sets the document to be auto-saved.
     *
     * @param document the document to track, or null to stop auto-saving
     */
    public void setCurrentDocument(ReadmeDocument document) {
        if (document != null) {
            currentDocument.set(document);
            lastSavedContent.set(document.getContent());
            LOG.debug("Auto-save tracking document: {}", document.getTitle());
        } else {
            currentDocument.set(null);
            lastSavedContent.set("");
        }
    }

    /**
     * Updates the tracked content (called when user types in the editor).
     * The auto-save task will compare this with the last saved content.
     *
     * @param newContent the current editor content
     */
    public void updateContent(String newContent) {
        ReadmeDocument doc = currentDocument.get();
        if (doc != null && !newContent.equals(doc.getContent())) {
            doc.setContent(newContent);
        }
    }

    /**
     * Enables or disables auto-saving.
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
        LOG.info("Auto-save {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Returns whether auto-saving is currently enabled.
     */
    public boolean isEnabled() {
        return enabled.get();
    }

    /**
     * Triggers an immediate save of the current document, if there are changes.
     */
    public void saveNow() {
        ReadmeDocument doc = currentDocument.get();
        if (doc == null || !enabled.get()) {
            return;
        }
        String currentContent = Optional.ofNullable(doc.getContent()).orElse("");
        String saved = lastSavedContent.get();
        if (!currentContent.equals(saved)) {
            try {
                doc.setContent(currentContent);
                readmeService.saveDocument(doc);
                lastSavedContent.set(currentContent);
                LOG.debug("Auto-save completed for document: {}", doc.getTitle());
            } catch (Exception e) {
                LOG.error("Auto-save failed for document: {}", doc.getTitle(), e);
            }
        }
    }

    /**
     * Starts the periodic auto-save task.
     */
    private void startAutoSaveTask(int intervalMs) {
        autoSaveTask = scheduler.scheduleAtFixedRate(
                this::saveNow, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        saveNow(); // Save any pending changes
        if (autoSaveTask != null) {
            autoSaveTask.cancel(false);
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOG.info("Auto-save manager shut down");
    }
}
