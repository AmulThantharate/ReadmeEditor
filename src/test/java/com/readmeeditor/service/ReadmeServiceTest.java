package com.readmeeditor.service;

import com.readmeeditor.model.ReadmeDocument;
import com.readmeeditor.repository.ReadmeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReadmeService}.
 */
@ExtendWith(MockitoExtension.class)
class ReadmeServiceTest {

    @Mock
    private ReadmeRepository readmeRepository;

    private ReadmeService readmeService;

    @BeforeEach
    void setUp() {
        VersionService versionService = new VersionService(readmeRepository);
        readmeService = new ReadmeService(readmeRepository, versionService);
    }

    @Test
    void createDocument_shouldGenerateIdAndSave() {
        // Arrange
        String userId = UUID.randomUUID().toString();
        when(readmeRepository.saveDocument(any(ReadmeDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ReadmeDocument doc = readmeService.createDocument("Test Title", "# Content", userId);

        // Assert
        assertNotNull(doc.getId());
        assertEquals("Test Title", doc.getTitle());
        assertEquals("# Content", doc.getContent());
        assertEquals(userId, doc.getCreatedByUserId());
        assertEquals(0, doc.getCurrentVersion());
        verify(readmeRepository, times(1)).saveDocument(any(ReadmeDocument.class));
    }

    @Test
    void createDocument_withNullTitle_shouldUseDefault() {
        // Arrange
        when(readmeRepository.saveDocument(any(ReadmeDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ReadmeDocument doc = readmeService.createDocument(null, "content", "user1");

        // Assert
        assertEquals("Untitled README", doc.getTitle());
    }

    @Test
    void createDocument_withNullContent_shouldUseEmptyString() {
        // Arrange
        when(readmeRepository.saveDocument(any(ReadmeDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ReadmeDocument doc = readmeService.createDocument("Title", null, "user1");

        // Assert
        assertEquals("", doc.getContent());
    }

    @Test
    void saveDocument_withoutId_shouldThrowException() {
        // Arrange
        ReadmeDocument doc = new ReadmeDocument();
        doc.setContent("Content without ID");

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> readmeService.saveDocument(doc));
    }

    @Test
    void findDocumentById_shouldReturnDocument() {
        // Arrange
        String docId = UUID.randomUUID().toString();
        ReadmeDocument expected = new ReadmeDocument(docId, "Test", "Content", "user1");
        when(readmeRepository.findDocumentById(docId)).thenReturn(Optional.of(expected));

        // Act
        Optional<ReadmeDocument> result = readmeService.findDocumentById(docId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(expected.getTitle(), result.get().getTitle());
    }

    @Test
    void findDocumentById_shouldReturnEmptyForMissing() {
        // Arrange
        when(readmeRepository.findDocumentById("missing")).thenReturn(Optional.empty());

        // Act
        Optional<ReadmeDocument> result = readmeService.findDocumentById("missing");

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void findAllDocuments_shouldReturnAll() {
        // Arrange
        List<ReadmeDocument> docs = List.of(
                new ReadmeDocument("1", "Doc1", "Content1", "user1"),
                new ReadmeDocument("2", "Doc2", "Content2", "user2")
        );
        when(readmeRepository.findAllDocuments()).thenReturn(docs);

        // Act
        List<ReadmeDocument> result = readmeService.findAllDocuments();

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    void deleteDocument_shouldCallRepository() {
        // Act
        readmeService.deleteDocument("doc1");

        // Assert
        verify(readmeRepository, times(1)).deleteDocument("doc1");
    }

    @Test
    void countDocuments_shouldReturnCount() {
        // Arrange
        when(readmeRepository.countDocuments()).thenReturn(5L);

        // Act
        long count = readmeService.countDocuments();

        // Assert
        assertEquals(5L, count);
    }
}
