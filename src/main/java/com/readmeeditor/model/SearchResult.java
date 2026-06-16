package com.readmeeditor.model;

import java.time.LocalDateTime;

/**
 * Model class representing a search result entry.
 * <p>
 * Contains the matching document along with score and context information
 * to display in the search results UI.
 */
public class SearchResult {
    private ReadmeDocument document;
    private double score;
    private String matchedContent;
    private LocalDateTime matchedAt;

    public SearchResult() {}

    public SearchResult(ReadmeDocument document, double score, String matchedContent) {
        this.document = document;
        this.score = score;
        this.matchedContent = matchedContent;
        this.matchedAt = LocalDateTime.now();
    }

    // --- Getters and Setters ---

    public ReadmeDocument getDocument() { return document; }
    public void setDocument(ReadmeDocument document) { this.document = document; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public String getMatchedContent() { return matchedContent; }
    public void setMatchedContent(String matchedContent) { this.matchedContent = matchedContent; }

    public LocalDateTime getMatchedAt() { return matchedAt; }
    public void setMatchedAt(LocalDateTime matchedAt) { this.matchedAt = matchedAt; }

    @Override
    public String toString() {
        return "SearchResult{document='" + document.getTitle() + "', score=" + score + "}";
    }
}
