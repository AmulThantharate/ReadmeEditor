package com.readmeeditor.service;

import com.readmeeditor.config.AppConfig;
import com.readmeeditor.config.RedisConfig;
import com.readmeeditor.model.ReadmeDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPooled;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TemplateService}.
 */
@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock
    private RedisConfig redisConfig;

    @Mock
    private Jedis jedis;

    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        lenient().when(redisConfig.getConnection()).thenReturn(jedis);
        templateService = new TemplateService(redisConfig);
    }

    @Test
    void getBuiltInTemplates_shouldReturnFiveTemplates() {
        // Act
        Map<String, String> templates = templateService.getBuiltInTemplates();

        // Assert
        assertEquals(5, templates.size());
        assertTrue(templates.containsKey("Project README"));
        assertTrue(templates.containsKey("Java Project"));
        assertTrue(templates.containsKey("Spring Boot"));
        assertTrue(templates.containsKey("Library"));
        assertTrue(templates.containsKey("API Documentation"));
    }

    @Test
    void getBuiltInTemplate_shouldReturnValidMarkdown() {
        // Act
        String content = templateService.getTemplate("Project README");

        // Assert
        assertNotNull(content);
        assertTrue(content.contains("# Project Name"));
        assertTrue(content.contains("## Features"));
        assertTrue(content.contains("## Installation"));
        assertTrue(content.contains("## Contributing"));
    }

    @Test
    void getBuiltInTemplate_withUnknownName_shouldReturnNull() {
        // Act
        String content = templateService.getTemplate("Non-existent Template");

        // Assert
        assertNull(content);
    }

    @Test
    void saveCustomTemplate_shouldPersistToRedis() {
        // Act
        templateService.saveCustomTemplate("user1", "My Template", "# My Custom Template");

        // Assert
        verify(jedis, times(1)).set("template:custom:user1:My Template", "# My Custom Template");
        verify(jedis, times(1)).sadd("template:custom:user:user1", "My Template");
    }

    @Test
    void getCustomTemplate_shouldRetrieveFromRedis() {
        // Arrange
        when(jedis.get("template:custom:user1:My Template")).thenReturn("# Hello");

        // Act
        String content = templateService.getCustomTemplate("user1", "My Template");

        // Assert
        assertEquals("# Hello", content);
    }

    @Test
    void deleteCustomTemplate_shouldRemoveFromRedis() {
        // Act
        templateService.deleteCustomTemplate("user1", "My Template");

        // Assert
        verify(jedis, times(1)).del("template:custom:user1:My Template");
        verify(jedis, times(1)).srem("template:custom:user:user1", "My Template");
    }

    @Test
    void getAllTemplates_shouldIncludeCustomTemplates() {
        // Arrange
        when(jedis.smembers("template:custom:user:user1")).thenReturn(Set.of("Custom One"));
        when(jedis.get("template:custom:user1:Custom One")).thenReturn("# Custom Content");

        // Act
        Map<String, String> all = templateService.getAllTemplates("user1");

        // Assert
        assertTrue(all.containsKey("Custom One"));
        assertEquals("# Custom Content", all.get("Custom One"));
        assertTrue(all.containsKey("Project README")); // Built-in should still be present
    }

    @Test
    void templateExists_shouldCheckBuiltInAndCustom() {
        // Arrange
        when(jedis.smembers("template:custom:user:user1")).thenReturn(Set.of("My Custom"));

        // Act & Assert
        assertTrue(templateService.templateExists("Project README", "user1")); // Built-in
        assertTrue(templateService.templateExists("My Custom", "user1")); // Custom
        assertFalse(templateService.templateExists("Non-existent", "user1")); // Neither
    }

    @Test
    void getJavaProjectTemplate_shouldContainJavaSpecificContent() {
        // Act
        String content = templateService.getTemplate("Java Project");

        // Assert
        assertNotNull(content);
        assertTrue(content.contains("Java 21"));
        assertTrue(content.contains("Maven"));
        assertTrue(content.contains("JUnit 5"));
        assertTrue(content.contains("mvn test"));
    }

    @Test
    void createFromTemplate_shouldThrowForUnknownTemplate() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> templateService.createFromTemplate("Unknown", "user1"));
    }
}
