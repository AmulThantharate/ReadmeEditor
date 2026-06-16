package com.readmeeditor.service;

import com.readmeeditor.util.MarkdownParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service for rendering Markdown to HTML with caching.
 * <p>
 * Caches rendered HTML to avoid re-parsing unchanged content.
 * The cache is invalidated when content changes.
 */
public class MarkdownService {
    private static final Logger LOG = LoggerFactory.getLogger(MarkdownService.class);

    private final ConcurrentMap<String, CachedRender> renderCache;

    public MarkdownService() {
        this.renderCache = new ConcurrentHashMap<>();
    }

    /**
     * Renders Markdown content to an HTML document for preview.
     * <p>
     * Uses a cache keyed by content hash to avoid re-rendering identical content.
     *
     * @param markdown the Markdown content to render
     * @return the full HTML document
     */
    public String renderToHtml(String markdown) {
        String cacheKey = generateCacheKey(markdown);

        CachedRender cached = renderCache.get(cacheKey);
        if (cached != null) {
            LOG.trace("Returning cached HTML for markdown (hash={})", cacheKey);
            return cached.html;
        }

        String html = MarkdownParser.toHtml(markdown);
        renderCache.put(cacheKey, new CachedRender(html, System.currentTimeMillis()));
        LOG.debug("Rendered markdown to HTML ({} chars -> {} chars)",
                markdown != null ? markdown.length() : 0, html.length());
        return html;
    }

    /**
     * Clears the render cache. Should be called when memory usage is a concern
     * or when the application is being closed.
     */
    public void clearCache() {
        renderCache.clear();
        LOG.debug("Render cache cleared");
    }

    /**
     * Returns the current cache size.
     */
    public int getCacheSize() {
        return renderCache.size();
    }

    /**
     * Generates a cache key from the content using a simple hash.
     */
    private String generateCacheKey(String content) {
        if (content == null) {
            return "__null__";
        }
        return String.valueOf(content.hashCode()) + "_" + content.length();
    }

    /**
     * Internal cache entry with timestamp for potential future TTL-based eviction.
     */
    private static class CachedRender {
        final String html;
        @SuppressWarnings("unused")
        final long cachedAt;

        CachedRender(String html, long cachedAt) {
            this.html = html;
            this.cachedAt = cachedAt;
        }
    }
}
