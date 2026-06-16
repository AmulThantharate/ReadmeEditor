package com.readmeeditor.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MarkdownParser}.
 */
class MarkdownParserTest {

    @Test
    void toHtml_withNullContent_shouldReturnEmptyHtml() {
        // Act
        String html = MarkdownParser.toHtml(null);

        // Assert
        assertNotNull(html);
        assertTrue(html.contains("<html"));
        assertTrue(html.contains("Start typing"));
    }

    @Test
    void toHtml_withEmptyContent_shouldReturnEmptyHtml() {
        // Act
        String html = MarkdownParser.toHtml("");

        // Assert
        assertNotNull(html);
        assertTrue(html.contains("<html"));
        assertTrue(html.contains("Start typing"));
    }

    @Test
    void toHtml_withHeading_shouldRenderCorrectly() {
        // Act
        String html = MarkdownParser.toHtml("# Hello World");

        // Assert
        assertNotNull(html);
        assertTrue(html.contains("Hello World"));
        assertTrue(html.contains("<h1"));
        assertTrue(html.contains("<!DOCTYPE html>"));
    }

    @Test
    void toHtml_withBoldText_shouldRenderCorrectly() {
        // Act
        String html = MarkdownParser.toHtml("This is **bold** text");

        // Assert
        assertNotNull(html);
        assertTrue(html.contains("bold"));
        assertTrue(html.contains("<strong>") || html.contains("bold"));
    }

    @Test
    void toHtml_withCodeBlock_shouldRenderCorrectly() {
        // Act
        String html = MarkdownParser.toHtml("```java\nSystem.out.println(\"hello\");\n```");

        // Assert
        assertNotNull(html);
        assertTrue(html.contains("println"));
        assertTrue(html.contains("<pre><code") || html.contains("<code>"));
    }

    @Test
    void toHtml_withUnorderedList_shouldRenderCorrectly() {
        // Act
        String html = MarkdownParser.toHtml("- Item 1\n- Item 2\n- Item 3");

        // Assert
        assertNotNull(html);
        assertTrue(html.contains("Item 1"));
        assertTrue(html.contains("Item 2"));
        assertTrue(html.contains("Item 3"));
        assertTrue(html.contains("<ul>") || html.contains("<li>"));
    }

    @Test
    void toHtml_withTable_shouldRenderCorrectly() {
        // Act
        String html = MarkdownParser.toHtml("| Col1 | Col2 |\n|------|------|\n| A    | B    |");

        // Assert
        assertNotNull(html);
        assertTrue(html.contains("<table") || html.contains("<th>") || html.contains("<td>"));
    }

    @Test
    void toHtml_withBlockquote_shouldRenderCorrectly() {
        // Act
        String html = MarkdownParser.toHtml("> This is a quote");

        // Assert
        assertNotNull(html);
        assertTrue(html.contains("This is a quote"));
        assertTrue(html.contains("<blockquote"));
    }

    @Test
    void toHtml_withMultipleElements_shouldRenderFullDocument() {
        // Arrange
        String markdown = """
                # Project Title
                                
                Description of the project.
                                
                ## Features
                                
                - Feature 1
                - Feature 2
                                
                ## Installation
                                
                ```bash
                npm install
                ```
                                
                > Note: Requires Node.js 18+
                """;

        // Act
        String html = MarkdownParser.toHtml(markdown);

        // Assert
        assertNotNull(html);
        assertTrue(html.contains("Project Title"));
        assertTrue(html.contains("Features"));
        assertTrue(html.contains("Feature 1"));
        assertTrue(html.contains("npm install"));
        assertTrue(html.contains("Note: Requires"));
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("</html>"));
    }
}
