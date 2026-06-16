package com.readmeeditor.service;

import com.readmeeditor.model.ReadmeDocument;
import com.readmeeditor.model.ReadmeVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Service for exporting README documents to the local filesystem and
 * importing existing README.md files into the application.
 * <p>
 * All exports are saved with UTF-8 encoding by default.
 */
public class ExportImportService {
    private static final Logger LOG = LoggerFactory.getLogger(ExportImportService.class);

    private static final String EXPORT_DIR = System.getProperty("user.home") + "/.readme-editor/exports";
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Exports a README document to the specified output path.
     *
     * @param doc      the document to export
     * @param filePath the full file path to export to (e.g., "/path/to/README.md")
     * @return the path to the exported file
     * @throws IOException if the file cannot be written
     */
    public Path exportToFile(ReadmeDocument doc, String filePath) throws IOException {
        Path path = Paths.get(filePath);

        // Create parent directories if needed
        Files.createDirectories(path.getParent());

        // Write the content
        Files.writeString(path, doc.getContent(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        LOG.info("Document '{}' exported to: {}", doc.getTitle(), path.toAbsolutePath());
        return path;
    }

    /**
     * Exports a README document to the default export directory with a timestamped filename.
     *
     * @param doc the document to export
     * @return the path to the exported file
     * @throws IOException if the file cannot be written
     */
    public Path exportToDefaultDir(ReadmeDocument doc) throws IOException {
        String timestamp = LocalDateTime.now().format(FILE_DATE_FORMAT);
        String safeTitle = doc.getTitle()
                .replaceAll("[^a-zA-Z0-9-_]", "_")
                .replaceAll("_+", "_")
                .toLowerCase();
        String fileName = safeTitle + "-" + timestamp + ".md";

        return exportToFile(doc, EXPORT_DIR + "/" + fileName);
    }

    /**
     * Exports a specific version of a document to a file.
     *
     * @param version the version to export
     * @param title   the document title (used for filename)
     * @return the path to the exported file
     * @throws IOException if the file cannot be written
     */
    public Path exportVersionToFile(ReadmeVersion version, String title) throws IOException {
        String timestamp = version.getCreatedAt().format(FILE_DATE_FORMAT);
        String safeTitle = title.replaceAll("[^a-zA-Z0-9-_]", "_").toLowerCase();
        String fileName = safeTitle + "-v" + version.getVersionNumber() + "-" + timestamp + ".md";

        Path path = Paths.get(EXPORT_DIR, fileName);
        Files.createDirectories(path.getParent());
        Files.writeString(path, version.getContent(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        LOG.info("Version {} of '{}' exported to: {}", version.getVersionNumber(), title, path.toAbsolutePath());
        return path;
    }

    /**
     * Imports a README.md file from the local filesystem.
     * <p>
     * Reads the file content and creates a new document in Redis.
     *
     * @param filePath    the path to the README.md file
     * @param userId      the ID of the user importing the file
     * @return the created ReadmeDocument
     * @throws IOException if the file cannot be read
     */
    public ReadmeDocument importFromFile(String filePath, String userId) throws IOException {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            throw new IOException("File not found: " + filePath);
        }
        if (!Files.isReadable(path)) {
            throw new IOException("File is not readable: " + filePath);
        }

        String content = Files.readString(path, StandardCharsets.UTF_8);

        // Derive title from filename
        String fileName = path.getFileName().toString();
        String title = fileName.replaceAll("(?i)\\.md$", "").replaceAll("[-_]", " ").trim();
        if (title.isEmpty()) {
            title = "Imported README";
        }

        ReadmeService readmeService = new ReadmeService();
        ReadmeDocument doc = readmeService.createDocument(title, content, userId);

        LOG.info("Document imported from '{}': {} (ID: {})", filePath, doc.getTitle(), doc.getId());
        return doc;
    }

    /**
     * Returns the default export directory path.
     */
    public String getExportDirectory() {
        return EXPORT_DIR;
    }

    /**
     * Ensures the export directory exists.
     */
    public void ensureExportDirectory() throws IOException {
        Files.createDirectories(Paths.get(EXPORT_DIR));
    }
}
