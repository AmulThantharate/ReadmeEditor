package com.readmeeditor.util;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Utility class for parsing Markdown content into HTML for preview rendering.
 * <p>
 * Uses the CommonMark library with GFM table and heading anchor extensions.
 */
public final class MarkdownParser {
    private static final Logger LOG = LoggerFactory.getLogger(MarkdownParser.class);

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;

    static {
        List<Extension> extensions = Arrays.asList(
                TablesExtension.create(),
                HeadingAnchorExtension.create()
        );
        PARSER = Parser.builder()
                .extensions(extensions)
                .build();
        RENDERER = HtmlRenderer.builder()
                .extensions(extensions)
                .escapeHtml(true)
                .sanitizeUrls(true)
                .build();
        LOG.debug("Markdown parser initialized with {} extensions", extensions.size());
    }

    private MarkdownParser() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Parses Markdown content into an HTML string suitable for WebView rendering.
     *
     * @param markdown the Markdown content to render
     * @return the full HTML document with styling
     */
    public static String toHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return wrapInHtmlDocument("<p><em>Start typing to preview your README...</em></p>");
        }
        try {
            Node document = PARSER.parse(markdown);
            String bodyHtml = RENDERER.render(document);
            return wrapInHtmlDocument(bodyHtml);
        } catch (Exception e) {
            LOG.error("Failed to parse Markdown content", e);
            return wrapInHtmlDocument("<p><strong>Error rendering Markdown.</strong></p>");
        }
    }

    /**
     * Wraps rendered HTML body in a complete HTML document with styling.
     */
    private static String wrapInHtmlDocument(String bodyHtml) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif;
                            font-size: 16px;
                            line-height: 1.6;
                            color: #24292f;
                            padding: 32px;
                            max-width: 800px;
                            margin: 0 auto;
                        }
                        h1 { border-bottom: 1px solid #d0d7de; padding-bottom: 8px; margin: 24px 0 16px; }
                        h2 { border-bottom: 1px solid #d0d7de; padding-bottom: 6px; margin: 20px 0 14px; }
                        h3, h4, h5, h6 { margin: 16px 0 12px; }
                        h1 { font-size: 2em; }
                        h2 { font-size: 1.5em; }
                        h3 { font-size: 1.25em; }
                        p { margin: 8px 0; }
                        ul, ol { padding-left: 24px; margin: 8px 0; }
                        li { margin: 4px 0; }
                        code {
                            background-color: rgba(175, 184, 193, 0.2);
                            border-radius: 4px;
                            padding: 2px 6px;
                            font-family: 'Cascadia Code', 'Fira Code', monospace;
                            font-size: 0.9em;
                        }
                        pre {
                            background-color: #f6f8fa;
                            border-radius: 6px;
                            padding: 16px;
                            overflow-x: auto;
                            margin: 16px 0;
                        }
                        pre code {
                            background: none;
                            padding: 0;
                            font-size: 0.85em;
                        }
                        blockquote {
                            border-left: 4px solid #d0d7de;
                            padding-left: 16px;
                            color: #57606a;
                            margin: 16px 0;
                        }
                        table {
                            border-collapse: collapse;
                            width: 100%;
                            margin: 16px 0;
                        }
                        th, td {
                            border: 1px solid #d0d7de;
                            padding: 8px 12px;
                            text-align: left;
                        }
                        th { background-color: #f6f8fa; font-weight: 600; }
                        tr:nth-child(even) { background-color: #f8f9fa; }
                        img { max-width: 100%; border-radius: 4px; }
                        a { color: #0969da; text-decoration: none; }
                        a:hover { text-decoration: underline; }
                        hr { border: none; border-top: 1px solid #d0d7de; margin: 24px 0; }
                    </style>
                    <script src="https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js"></script>
                    <script>
                        document.addEventListener("DOMContentLoaded", function() {
                            const blocks = document.querySelectorAll("pre code.language-mermaid");
                            if (blocks.length > 0) {
                                blocks.forEach(block => {
                                    const pre = block.parentNode;
                                    const div = document.createElement("div");
                                    div.className = "mermaid";
                                    div.textContent = block.textContent;
                                    pre.replaceWith(div);
                                });
                                const isDark = window.parent && window.parent.document && window.parent.document.body && window.parent.document.body.classList.contains("dark-theme");
                                mermaid.initialize({
                                    startOnLoad: false,
                                    theme: isDark ? 'dark' : 'default',
                                    securityLevel: 'loose'
                                });
                                mermaid.run();
                            }
                        });
                    </script>
                </head>
                <body>""" +
                bodyHtml +
                """
                </body>
                </html>""";
    }
}
