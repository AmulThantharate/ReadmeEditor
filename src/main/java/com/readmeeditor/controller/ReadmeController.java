package com.readmeeditor.controller;

import com.readmeeditor.model.ReadmeDocument;
import com.readmeeditor.model.ReadmeVersion;
import com.readmeeditor.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Controller for README document operations triggered by the UI.
 * <p>
 * Handles creating, opening, saving, auto-saving, and tag management
 * for README documents.
 */
public class ReadmeController {
    private static final Logger LOG = LoggerFactory.getLogger(ReadmeController.class);

    private final ReadmeService readmeService;
    private final MarkdownService markdownService;
    private final ExportImportService exportImportService;

    public ReadmeController() {
        this.readmeService = new ReadmeService();
        this.markdownService = new MarkdownService();
        this.exportImportService = new ExportImportService();
    }

    public ReadmeController(ReadmeService readmeService, MarkdownService markdownService,
                            ExportImportService exportImportService) {
        this.readmeService = readmeService;
        this.markdownService = markdownService;
        this.exportImportService = exportImportService;
    }

    /**
     * Creates a new README document.
     */
    public ReadmeDocument createDocument(String title, String content, String userId) {
        return readmeService.createDocument(title, content, userId);
    }

    /**
     * Opens (finds) a document by its ID.
     */
    public Optional<ReadmeDocument> openDocument(String id) {
        return readmeService.findDocumentById(id);
    }

    /**
     * Saves a document with a new version.
     */
    public ReadmeDocument saveDocument(ReadmeDocument doc, String userId, String commitMessage) {
        return readmeService.saveWithVersion(doc, userId, commitMessage);
    }

    /**
     * Auto-saves a document without creating a version entry.
     */
    public ReadmeDocument autoSave(ReadmeDocument doc) {
        return readmeService.saveDocument(doc);
    }

    /**
     * Renders Markdown content to HTML for the preview pane.
     */
    public String renderPreview(String markdown) {
        return markdownService.renderToHtml(markdown);
    }

    /**
     * Returns all documents for a user.
     */
    public List<ReadmeDocument> getUserDocuments(String userId) {
        return readmeService.findDocumentsByUserId(userId);
    }

    /**
     * Exports a document to a file on disk.
     */
    public java.nio.file.Path exportDocument(ReadmeDocument doc, String filePath) throws java.io.IOException {
        return exportImportService.exportToFile(doc, filePath);
    }

    /**
     * Imports a README file from disk.
     */
    public ReadmeDocument importDocument(String filePath, String userId) throws java.io.IOException {
        return exportImportService.importFromFile(filePath, userId);
    }

    /**
     * Deletes a document.
     */
    public void deleteDocument(String id) {
        readmeService.deleteDocument(id);
    }

    /**
     * Lists all tags for a document.
     */
    public com.readmeeditor.model.Tag getTag(String tagId) {
        return new com.readmeeditor.repository.ReadmeRepository()
                .findTagById(tagId).orElse(null);
    }
}
