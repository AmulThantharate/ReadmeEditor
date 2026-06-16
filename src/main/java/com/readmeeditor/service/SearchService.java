package com.readmeeditor.service;

import com.readmeeditor.model.ReadmeDocument;
import com.readmeeditor.model.SearchResult;
import com.readmeeditor.repository.ReadmeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for searching README documents by title, content, tags, and date.
 * <p>
 * Provides ranked search results with relevance scoring.
 */
public class SearchService {
    private static final Logger LOG = LoggerFactory.getLogger(SearchService.class);

    private final ReadmeRepository readmeRepository;

    public SearchService() {
        this.readmeRepository = new ReadmeRepository();
    }

    public SearchService(ReadmeRepository readmeRepository) {
        this.readmeRepository = readmeRepository;
    }

    /**
     * Searches documents by query string (matches against title and content).
     * Results are scored based on relevance:
     * - Title matches score higher than content matches
     * - Exact matches score higher than partial matches
     *
     * @param query the search query
     * @return ranked list of search results
     */
    public List<SearchResult> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        String lowerQuery = query.toLowerCase().trim();
        List<ReadmeDocument> allDocs = readmeRepository.findAllDocuments();

        return allDocs.stream()
                .map(doc -> scoreDocument(doc, lowerQuery))
                .filter(result -> result.getScore() > 0)
                .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Searches documents filtered by tags.
     *
     * @param query  the search query (can be empty for tag-only filter)
     * @param tagIds list of tag IDs to filter by
     * @return filtered and ranked search results
     */
    public List<SearchResult> searchWithTags(String query, List<String> tagIds) {
        List<SearchResult> results = search(query);

        if (tagIds == null || tagIds.isEmpty()) {
            return results;
        }

        return results.stream()
                .filter(result -> result.getDocument().getTagIds().stream()
                        .anyMatch(tagIds::contains))
                .collect(Collectors.toList());
    }

    /**
     * Searches documents filtered by creation date range.
     *
     * @param query   the search query (can be empty)
     * @param fromDate the start date (inclusive)
     * @param toDate   the end date (inclusive)
     * @return filtered and ranked search results
     */
    public List<SearchResult> searchByDateRange(String query, LocalDate fromDate, LocalDate toDate) {
        List<SearchResult> results = search(query);

        return results.stream()
                .filter(result -> {
                    LocalDateTime created = result.getDocument().getCreatedAt();
                    LocalDate createdDate = created.toLocalDate();
                    return (fromDate == null || !createdDate.isBefore(fromDate))
                            && (toDate == null || !createdDate.isAfter(toDate));
                })
                .collect(Collectors.toList());
    }

    /**
     * Searches content within a specific document.
     *
     * @param documentId the document ID
     * @param query      the search query
     * @return matching content snippets
     */
    public List<String> searchWithinDocument(String documentId, String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        return readmeRepository.findDocumentById(documentId)
                .map(doc -> findMatchingLines(doc.getContent(), query.toLowerCase()))
                .orElse(List.of());
    }

    /**
     * Scores a document against a query and returns a SearchResult.
     */
    private SearchResult scoreDocument(ReadmeDocument doc, String lowerQuery) {
        double score = 0.0;
        String matchedContent = "";

        String title = doc.getTitle() != null ? doc.getTitle().toLowerCase() : "";
        String content = doc.getContent() != null ? doc.getContent().toLowerCase() : "";

        // Exact title match: highest score
        if (title.equals(lowerQuery)) {
            score += 100.0;
            matchedContent = doc.getTitle();
        }
        // Title contains query
        else if (title.contains(lowerQuery)) {
            score += 50.0;
            matchedContent = doc.getTitle();
        }
        // Content contains exact phrase
        else if (content.contains(lowerQuery)) {
            score += 10.0;
            matchedContent = extractSnippet(doc.getContent(), lowerQuery);
        }
        // Individual word matching
        else {
            String[] queryWords = lowerQuery.split("\\s+");
            int wordMatches = 0;
            for (String word : queryWords) {
                if (word.length() > 1) {
                    if (title.contains(word)) {
                        wordMatches += 3;
                    }
                    if (content.contains(word)) {
                        wordMatches += 1;
                    }
                }
            }
            if (wordMatches > 0) {
                score = wordMatches;
                matchedContent = extractSnippet(doc.getContent(), queryWords[0]);
            }
        }

        return score > 0
                ? new SearchResult(doc, score, matchedContent)
                : new SearchResult(doc, 0, "");
    }

    /**
     * Extracts a relevant snippet of text around the first match.
     */
    private String extractSnippet(String content, String query) {
        if (content == null || query == null) {
            return "";
        }
        int index = content.toLowerCase().indexOf(query.toLowerCase());
        if (index < 0) {
            return content.length() > 100 ? content.substring(0, 100) + "..." : content;
        }

        int start = Math.max(0, index - 60);
        int end = Math.min(content.length(), index + query.length() + 60);
        String snippet = content.substring(start, end);

        if (start > 0) snippet = "..." + snippet;
        if (end < content.length()) snippet = snippet + "...";

        return snippet;
    }

    /**
     * Finds matching lines in content for a query.
     */
    private List<String> findMatchingLines(String content, String lowerQuery) {
        if (content == null) {
            return List.of();
        }
        return content.lines()
                .filter(line -> line.toLowerCase().contains(lowerQuery))
                .map(String::trim)
                .collect(Collectors.toList());
    }
}
