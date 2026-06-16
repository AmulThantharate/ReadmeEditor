package com.readmeeditor.service;

import com.readmeeditor.model.ReadmeDocument;
import com.readmeeditor.model.ReadmeVersion;
import com.readmeeditor.repository.ReadmeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VersionService}.
 */
@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

    @Mock
    private ReadmeRepository readmeRepository;

    private VersionService versionService;

    @BeforeEach
    void setUp() {
        versionService = new VersionService(readmeRepository);
    }

    @Test
    void getVersions_shouldReturnAllVersions() {
        // Arrange
        String docId = "doc1";
        List<ReadmeVersion> versions = List.of(
                new ReadmeVersion("v1", docId, 1, "content1", "user1", "Initial"),
                new ReadmeVersion("v2", docId, 2, "content2", "user2", "Update")
        );
        when(readmeRepository.getVersions(docId)).thenReturn(versions);

        // Act
        List<ReadmeVersion> result = versionService.getVersions(docId);

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    void getVersion_shouldReturnSpecificVersion() {
        // Arrange
        String docId = "doc1";
        ReadmeVersion version = new ReadmeVersion("v1", docId, 1, "content", "user1", "Initial");
        when(readmeRepository.getVersion(docId, 1)).thenReturn(Optional.of(version));

        // Act
        Optional<ReadmeVersion> result = versionService.getVersion(docId, 1);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(1, result.get().getVersionNumber());
        assertEquals("Initial", result.get().getCommitMessage());
    }

    @Test
    void restoreVersion_shouldCreateNewVersionWithRestoredContent() {
        // Arrange
        String docId = "doc1";
        ReadmeDocument originalDoc = new ReadmeDocument(docId, "Test", "Current content", "user1");
        originalDoc.setCurrentVersion(3);

        ReadmeVersion oldVersion = new ReadmeVersion("v1", docId, 1, "Restored content", "user1", "Initial");

        when(readmeRepository.findDocumentById(docId)).thenReturn(Optional.of(originalDoc));
        when(readmeRepository.getVersion(docId, 1)).thenReturn(Optional.of(oldVersion));
        when(readmeRepository.saveDocument(any(ReadmeDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ReadmeDocument restored = versionService.restoreVersion(docId, 1, "user2");

        // Assert
        assertEquals("Restored content", restored.getContent());
        assertEquals(4, restored.getCurrentVersion()); // Incremented
        verify(readmeRepository, times(1)).saveVersion(any(ReadmeVersion.class));
    }

    @Test
    void restoreVersion_withMissingDocument_shouldThrow() {
        // Arrange
        when(readmeRepository.findDocumentById("missing")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> versionService.restoreVersion("missing", 1, "user1"));
    }

    @Test
    void restoreVersion_withMissingVersion_shouldThrow() {
        // Arrange
        ReadmeDocument doc = new ReadmeDocument("doc1", "Test", "Content", "user1");
        when(readmeRepository.findDocumentById("doc1")).thenReturn(Optional.of(doc));
        when(readmeRepository.getVersion("doc1", 99)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> versionService.restoreVersion("doc1", 99, "user1"));
    }

    @Test
    void compareVersions_shouldReturnDiff() {
        // Arrange
        String docId = "doc1";
        ReadmeVersion oldV = new ReadmeVersion("v1", docId, 1, "Line A\nLine B\nLine C", "user1", "First");
        ReadmeVersion newV = new ReadmeVersion("v2", docId, 2, "Line A\nLine X\nLine C", "user2", "Changed B->X");

        when(readmeRepository.getVersion(docId, 1)).thenReturn(Optional.of(oldV));
        when(readmeRepository.getVersion(docId, 2)).thenReturn(Optional.of(newV));

        // Act
        String diff = versionService.compareVersions(docId, 1, 2);

        // Assert
        assertTrue(diff.contains("Line 2"));
        assertTrue(diff.contains("- Line B"));
        assertTrue(diff.contains("+ Line X"));
    }

    @Test
    void compareVersions_identicalContent_shouldReturnNoDifference() {
        // Arrange
        String docId = "doc1";
        ReadmeVersion oldV = new ReadmeVersion("v1", docId, 1, "Same content", "user1", "First");
        ReadmeVersion newV = new ReadmeVersion("v2", docId, 2, "Same content", "user2", "Second");

        when(readmeRepository.getVersion(docId, 1)).thenReturn(Optional.of(oldV));
        when(readmeRepository.getVersion(docId, 2)).thenReturn(Optional.of(newV));

        // Act
        String diff = versionService.compareVersions(docId, 1, 2);

        // Assert
        assertTrue(diff.contains("No differences"));
    }

    @Test
    void getVersionCount_shouldReturnCount() {
        // Arrange
        when(readmeRepository.getVersions("doc1")).thenReturn(List.of(
                new ReadmeVersion("v1", "doc1", 1, "c", "u", "m"),
                new ReadmeVersion("v2", "doc1", 2, "c", "u", "m"),
                new ReadmeVersion("v3", "doc1", 3, "c", "u", "m")
        ));

        // Act
        int count = versionService.getVersionCount("doc1");

        // Assert
        assertEquals(3, count);
    }
}
