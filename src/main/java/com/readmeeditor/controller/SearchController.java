package com.readmeeditor.controller;

import com.readmeeditor.model.SearchResult;
import com.readmeeditor.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller for search operations triggered from the UI.
 * <p>
 * Handles searching documents by text, tags, date ranges, and
 * content within a specific document.
 */
public class SearchController {
    private static final Logger LOG = LoggerFactory.getLogger(SearchController.class);

    private final SearchService searchService;

    public SearchController() {
        this.searchService = new SearchService();
    }

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Searches all documents by query text.
     *
     * @param query the search query
     * @return ranked search results
     */
    public List<SearchResult> search(String query) {
        LOG.debug("Search query: '{}'", query);
        return searchService.search(query);
    }

    /**
     * Searches documents filtered by tags.
     *
     * @param query  the search query (can be empty)
     * @param tagIds tag IDs to filter by
     * @return filtered search results
     */
    public List<SearchResult> searchWithTags(String query, List<String> tagIds) {
        return searchService.searchWithTags(query, tagIds);
    }

    /**
     * Searches documents within a date range.
     *
     * @param query   the search query (can be empty)
     * @param from    start date (inclusive)
     * @param to      end date (inclusive)
     * @return filtered search results
     */
    public List<SearchResult> searchByDateRange(String query, LocalDate from, LocalDate to) {
        return searchService.searchByDateRange(query, from, to);
    }

    /**
     * Searches within a specific document.
     *
     * @param documentId the document to search within
     * @param query      the search text
     * @return matching content lines
     */
    public List<String> searchWithinDocument(String documentId, String query) {
        LOG.debug("Search within document {}: '{}'", documentId, query);
        return searchService.searchWithinDocument(documentId, query);
    }
}
