package com.readmeeditor.controller;

import com.readmeeditor.model.ReadmeDocument;
import com.readmeeditor.model.User;
import com.readmeeditor.service.ReadmeService;
import com.readmeeditor.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Controller for the main dashboard view.
 * <p>
 * Provides aggregated statistics about README documents, recent activity,
 * and storage usage for the currently logged-in user.
 */
public class DashboardController {
    private static final Logger LOG = LoggerFactory.getLogger(DashboardController.class);

    private final ReadmeService readmeService;
    private final UserService userService;

    public DashboardController() {
        this.readmeService = new ReadmeService();
        this.userService = new UserService();
    }

    public DashboardController(ReadmeService readmeService, UserService userService) {
        this.readmeService = readmeService;
        this.userService = userService;
    }

    /**
     * Returns the total number of README documents across all users.
     */
    public long getTotalReadmeFiles() {
        return readmeService.countDocuments();
    }

    /**
     * Returns the total number of registered users.
     */
    public long getTotalUsers() {
        return userService.countUsers();
    }

    /**
     * Returns the total number of document versions across all documents.
     */
    public long getTotalVersions() {
        return readmeService.countTotalVersions();
    }

    /**
     * Returns the total number of documents created by a specific user.
     */
    public long getUserDocumentCount(String userId) {
        return readmeService.findDocumentsByUserId(userId).size();
    }

    /**
     * Returns the most recently updated documents.
     *
     * @param limit the maximum number to return
     */
    public List<ReadmeDocument> getRecentDocuments(int limit) {
        return readmeService.findRecentDocuments(limit);
    }

    /**
     * Returns the most edited documents (by version count).
     *
     * @param limit the maximum number to return
     */
    public List<ReadmeDocument> getMostEditedDocuments(int limit) {
        return readmeService.findAllDocuments().stream()
                .sorted((a, b) -> Integer.compare(b.getCurrentVersion(), a.getCurrentVersion()))
                .limit(limit)
                .toList();
    }

    /**
     * Returns recent documents created by a specific user.
     */
    public List<ReadmeDocument> getUserRecentDocuments(String userId, int limit) {
        return readmeService.findDocumentsByUserId(userId).stream()
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .limit(limit)
                .toList();
    }

    /**
     * Returns all documents for a user.
     */
    public List<ReadmeDocument> getUserDocuments(String userId) {
        return readmeService.findDocumentsByUserId(userId);
    }

    /**
     * Calculates approximate storage statistics.
     * <p>
     * Estimates Redis memory usage based on document and version counts.
     *
     * @param userId the user ID for per-user stats, or null for global stats
     * @return a map with storage statistics
     */
    public java.util.Map<String, Object> getStorageStatistics(String userId) {
        java.util.Map<String, Object> stats = new java.util.LinkedHashMap<>();

        List<ReadmeDocument> docs;
        if (userId != null) {
            docs = readmeService.findDocumentsByUserId(userId);
        } else {
            docs = readmeService.findAllDocuments();
        }

        long totalContentBytes = docs.stream()
                .mapToLong(d -> {
                    String content = d.getContent();
                    return content != null ? content.length() : 0;
                })
                .sum();

        stats.put("documentCount", docs.size());
        stats.put("totalContentSizeKB", String.format("%.1f", totalContentBytes / 1024.0));
        stats.put("avgDocumentSizeKB", docs.isEmpty() ? "0.0"
                : String.format("%.1f", totalContentBytes / (docs.size() * 1024.0)));
        stats.put("totalVersions", readmeService.countTotalVersions());

        return stats;
    }

    /**
     * Searches for users by username (for admin dashboard).
     */
    public List<User> searchUsers(String query) {
        return userService.findAllUsers().stream()
                .filter(u -> u.getUsername().toLowerCase().contains(query.toLowerCase()))
                .toList();
    }
}
