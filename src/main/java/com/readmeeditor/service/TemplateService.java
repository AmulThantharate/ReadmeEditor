package com.readmeeditor.service;

import com.readmeeditor.config.RedisConfig;
import com.readmeeditor.model.ReadmeDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.util.*;

/**
 * Service providing README templates for common project types and
 * supporting user-defined custom templates persisted to Redis.
 * <p>
 * Built-in templates are available for: Project README, Java Project,
 * Spring Boot, Library, and API Documentation.
 * <p>
 * Custom templates are stored in Redis under the key {@code template:custom:{userId}:{name}}.
 */
public class TemplateService {
    private static final Logger LOG = LoggerFactory.getLogger(TemplateService.class);

    private static final String CUSTOM_TEMPLATE_PREFIX = "template:custom:";
    private static final String CUSTOM_TEMPLATE_INDEX = "template:custom:user:";

    private final RedisConfig redisConfig;

    public TemplateService() {
        this.redisConfig = RedisConfig.getInstance();
    }

    TemplateService(RedisConfig redisConfig) {
        this.redisConfig = redisConfig;
    }

    // ==================== Built-in Templates ====================

    /**
     * Returns a map of built-in template names to their Markdown content.
     */
    public Map<String, String> getBuiltInTemplates() {
        Map<String, String> templates = new LinkedHashMap<>();
        templates.put("Project README", getProjectReadmeTemplate());
        templates.put("Java Project", getJavaProjectTemplate());
        templates.put("Spring Boot", getSpringBootTemplate());
        templates.put("Library", getLibraryTemplate());
        templates.put("API Documentation", getApiDocumentationTemplate());
        return templates;
    }

    /**
     * Returns all available templates (built-in + user's custom templates).
     * <p>
     * Custom templates that share a name with a built-in template are prefixed
     * with "[Custom] " to avoid ambiguity.
     *
     * @param userId the user ID for custom template lookup, or null for built-in only
     * @return map of template name to content
     */
    public Map<String, String> getAllTemplates(String userId) {
        Map<String, String> allTemplates = getBuiltInTemplates();

        if (userId != null) {
            Map<String, String> customTemplates = getCustomTemplates(userId);
            for (Map.Entry<String, String> entry : customTemplates.entrySet()) {
                String name = entry.getKey();
                String content = entry.getValue();
                // Prefix custom templates that collide with built-in names
                if (getBuiltInTemplates().containsKey(name)) {
                    name = "[Custom] " + name;
                }
                allTemplates.put(name, content);
            }
        }

        return allTemplates;
    }

    /**
     * Gets a specific template by name (checks built-in first, then custom).
     *
     * @param templateName the template name
     * @param userId       the user ID for custom template lookup
     * @return the template content, or null if not found
     */
    public String getTemplate(String templateName, String userId) {
        String content = getBuiltInTemplates().get(templateName);
        if (content != null) {
            return content;
        }
        if (userId != null) {
            return getCustomTemplate(userId, templateName);
        }
        return null;
    }

    /**
     * Gets a specific built-in template by name (backward-compatible).
     *
     * @param templateName the template name
     * @return the template content, or null if not found
     */
    public String getTemplate(String templateName) {
        return getBuiltInTemplates().get(templateName);
    }

    /**
     * Creates a new document from a template (built-in or custom).
     *
     * @param templateName the name of the template to use
     * @param userId       the creating user's ID
     * @return a new ReadmeDocument pre-populated with the template content
     * @throws IllegalArgumentException if the template is not found
     */
    public ReadmeDocument createFromTemplate(String templateName, String userId) {
        String content = getTemplate(templateName, userId);
        if (content == null) {
            throw new IllegalArgumentException("Template not found: " + templateName);
        }

        ReadmeService readmeService = new ReadmeService();
        return readmeService.createDocument(templateName + " - README", content, userId);
    }

    // ==================== Custom Template Persistence ====================

    /**
     * Saves a custom template for a user.
     * <p>
     * Custom templates are stored under the key {@code template:custom:{userId}:{name}}.
     * If a custom template shares its name with a built-in template, it is displayed
     * with a "[Custom] " prefix in {@link #getAllTemplates(String)} to avoid ambiguity.
     *
     * @param userId   the user ID (required)
     * @param name     the template name (required)
     * @param content  the Markdown template content (required)
     * @throws NullPointerException if any parameter is null
     */
    public void saveCustomTemplate(String userId, String name, String content) {
        Objects.requireNonNull(userId, "User ID must not be null");
        Objects.requireNonNull(name, "Template name must not be null");
        Objects.requireNonNull(content, "Template content must not be null");

        try (Jedis jedis = redisConfig.getConnection()) {
            String key = CUSTOM_TEMPLATE_PREFIX + userId + ":" + name;
            jedis.set(key, content);
            jedis.sadd(CUSTOM_TEMPLATE_INDEX + userId, name);
            LOG.debug("Custom template saved for user {}: {}", userId, name);
        }
    }

    /**
     * Returns all custom templates for a user.
     *
     * @param userId the user ID
     * @return map of template name to content
     */
    public Map<String, String> getCustomTemplates(String userId) {
        Objects.requireNonNull(userId, "User ID must not be null");
        Map<String, String> templates = new LinkedHashMap<>();

        try (Jedis jedis = redisConfig.getConnection()) {
            Set<String> templateNames = jedis.smembers(CUSTOM_TEMPLATE_INDEX + userId);
            for (String name : templateNames) {
                String content = getCustomTemplate(userId, name);
                if (content != null) {
                    templates.put(name, content);
                }
            }
        }

        return templates;
    }

    /**
     * Gets a specific custom template for a user.
     *
     * @param userId the user ID
     * @param name   the template name
     * @return the template content, or null if not found
     */
    public String getCustomTemplate(String userId, String name) {
        try (Jedis jedis = redisConfig.getConnection()) {
            return jedis.get(CUSTOM_TEMPLATE_PREFIX + userId + ":" + name);
        }
    }

    /**
     * Deletes a custom template.
     *
     * @param userId the user ID
     * @param name   the template name to delete
     */
    public void deleteCustomTemplate(String userId, String name) {
        try (Jedis jedis = redisConfig.getConnection()) {
            jedis.del(CUSTOM_TEMPLATE_PREFIX + userId + ":" + name);
            jedis.srem(CUSTOM_TEMPLATE_INDEX + userId, name);
            LOG.debug("Custom template deleted: {} for user {}", name, userId);
        }
    }

    /**
     * Returns the names of all custom templates for a user.
     *
     * @param userId the user ID
     * @return set of template names
     */
    public Set<String> getCustomTemplateNames(String userId) {
        try (Jedis jedis = redisConfig.getConnection()) {
            return jedis.smembers(CUSTOM_TEMPLATE_INDEX + userId);
        }
    }

    /**
     * Checks if a template name exists (built-in or custom).
     *
     * @param name   the template name
     * @param userId the user ID for custom template check
     * @return true if the template exists
     */
    public boolean templateExists(String name, String userId) {
        if (getBuiltInTemplates().containsKey(name)) {
            return true;
        }
        return getCustomTemplateNames(userId).contains(name);
    }

    // ==================== Built-in Template Content ====================

    private String getProjectReadmeTemplate() {
        return """
                # Project Name

                > A brief, compelling description of your project.

                ## Table of Contents

                - [Features](#features)
                - [Prerequisites](#prerequisites)
                - [Installation](#installation)
                - [Configuration](#configuration)
                - [Usage](#usage)
                - [Project Structure](#project-structure)
                - [Contributing](#contributing)
                - [License](#license)

                ## Features

                - ✨ Feature 1 — Brief description
                - 🚀 Feature 2 — Brief description
                - 📦 Feature 3 — Brief description

                ## Prerequisites

                Before you begin, ensure you have the following installed:

                - [Tool / Runtime](https://example.com) version X.Y or higher
                - [Dependency](https://example.com) version X.Y or higher

                ## Installation

                ```bash
                # Clone the repository
                git clone https://github.com/username/project.git
                cd project

                # Install dependencies
                [install command]

                # Build the project
                [build command]
                ```

                ## Configuration

                Create a `.env` file in the project root:

                ```env
                DATABASE_URL=postgresql://localhost:5432/mydb
                API_KEY=your_api_key_here
                ```

                ## Usage

                ```bash
                # Start the application
                [start command]

                # Run tests
                [test command]
                ```

                ### Examples

                ```python
                # Example code snippet
                from project import Client
                client = Client(api_key="your_key")
                result = client.process(data)
                print(result)
                ```

                ## Project Structure

                ```
                project/
                ├── src/           # Source code
                ├── tests/         # Test files
                ├── docs/          # Documentation
                ├── scripts/       # Utility scripts
                ├── .env.example   # Environment variables template
                ├── .gitignore
                ├── package.json   # Dependencies (if applicable)
                └── README.md      # This file
                ```

                ## Contributing

                1. Fork the repository
                2. Create a feature branch (`git checkout -b feature/amazing-feature`)
                3. Commit your changes (`git commit -m 'Add amazing feature'`)
                4. Push to the branch (`git push origin feature/amazing-feature`)
                5. Open a Pull Request

                ## License

                Distributed under the MIT License. See `LICENSE` for more information.

                ---

                Made with ❤️ by [Your Name](https://github.com/username)
                """;
    }

    private String getJavaProjectTemplate() {
        return """
                # Java Project

                > A Java application built with modern tooling and best practices.

                ## Table of Contents

                - [Overview](#overview)
                - [Tech Stack](#tech-stack)
                - [Prerequisites](#prerequisites)
                - [Getting Started](#getting-started)
                - [Building](#building)
                - [Testing](#testing)
                - [Project Structure](#project-structure)
                - [Contributing](#contributing)

                ## Overview

                Describe what this Java project does and why it exists.

                ## Tech Stack

                - **Language:** Java 21
                - **Build Tool:** Maven
                - **Testing:** JUnit 5 + Mockito

                ## Prerequisites

                - JDK 21 or later
                - Apache Maven 3.9+

                ## Getting Started

                ```bash
                # Clone the repository
                git clone https://github.com/username/java-project.git
                cd java-project

                # Build the project
                mvn clean install

                # Run the application
                mvn exec:java
                ```

                ## Building

                ```bash
                # Compile
                mvn compile

                # Package
                mvn package

                # Build without tests
                mvn package -DskipTests
                ```

                ## Testing

                ```bash
                # Run all tests
                mvn test

                # Run specific test class
                mvn test -Dtest=ExampleTest

                # Run with coverage report
                mvn verify
                ```

                ## Project Structure

                ```
                src/
                ├── main/
                │   ├── java/com/project/
                │   │   ├── config/     # Configuration classes
                │   │   ├── controller/ # API controllers
                │   │   ├── service/    # Business logic
                │   │   ├── repository/ # Data access
                │   │   ├── model/      # Domain models
                │   │   └── util/       # Utilities
                │   └── resources/      # Config files
                └── test/
                    └── java/com/project/  # Unit tests
                ```

                ## License

                This project is licensed under the MIT License.
                """;
    }

    private String getSpringBootTemplate() {
        return """
                # Spring Boot Application

                > A production-ready Spring Boot microservice.

                ## Overview

                Brief description of your Spring Boot application and its purpose.

                ## Tech Stack

                - **Framework:** Spring Boot 3.x
                - **Language:** Java 21
                - **Build Tool:** Maven / Gradle
                - **Database:** PostgreSQL / MongoDB
                - **Testing:** JUnit 5 + Testcontainers

                ## Quick Start

                ### Prerequisites

                - JDK 21
                - Docker & Docker Compose

                ### Running with Docker

                ```bash
                docker-compose up -d
                ```

                ### Running Locally

                ```bash
                ./mvnw spring-boot:run
                ```

                ## API Endpoints

                | Method | Endpoint       | Description          |
                |--------|---------------|----------------------|
                | GET    | `/api/health` | Health check         |
                | GET    | `/api/users`  | List all users       |
                | POST   | `/api/users`  | Create a new user    |
                | GET    | `/api/users/{id}` | Get user by ID  |

                ## Configuration

                ```yaml
                spring:
                  datasource:
                    url: jdbc:postgresql://localhost:5432/mydb
                    username: app_user
                    password: ${DB_PASSWORD}
                ```

                ## Testing

                ```bash
                ./mvnw test

                # Integration tests with Testcontainers
                ./mvnw verify
                ```

                ## Actuator Endpoints

                - `/actuator/health` — Application health
                - `/actuator/metrics` — Application metrics
                - `/actuator/info` — Application info

                ## License

                MIT
                """;
    }

    private String getLibraryTemplate() {
        return """
                # Library Name

                > A lightweight, zero-dependency library for [purpose].

                [![Maven Central](https://img.shields.io/maven-central/v/com.example/library)](https://search.maven.org/)
                [![Javadoc](https://javadoc.io/badge2/com.example/library/javadoc.svg)](https://javadoc.io/doc/com.example/library)

                ## Installation

                ### Maven

                ```xml
                <dependency>
                    <groupId>com.example</groupId>
                    <artifactId>library</artifactId>
                    <version>1.0.0</version>
                </dependency>
                ```

                ### Gradle

                ```groovy
                implementation 'com.example:library:1.0.0'
                ```

                ## Quick Start

                ```java
                import com.example.library.Client;

                Client client = new Client.Builder()
                        .withConfig(config)
                        .build();

                Result result = client.process(input);
                System.out.println(result.getValue());
                ```

                ## API Reference

                ### `Client`

                The main entry point for interacting with the library.

                | Method      | Returns   | Description                |
                |-------------|-----------|----------------------------|
                | `process()` | `Result`  | Process the given input    |
                | `validate()`| `boolean` | Validate input data        |

                ### `Config`

                Configuration options for the client.

                - `timeout` — Request timeout in milliseconds (default: 5000)
                - `retries` — Number of retry attempts (default: 3)
                - `logLevel` — Logging level (default: INFO)

                ## Building from Source

                ```bash
                git clone https://github.com/username/library.git
                cd library
                ./gradlew build
                ```

                ## Contributing

                Contributions are welcome! Please read our [contributing guidelines](CONTRIBUTING.md).

                ## License

                Apache 2.0
                """;
    }

    private String getApiDocumentationTemplate() {
        return """
                # API Documentation

                > Version 1.0.0

                ## Base URL

                ```
                https://api.example.com/v1
                ```

                ## Authentication

                All API requests require authentication via Bearer token:

                ```
                Authorization: Bearer <your-api-token>
                ```

                To obtain an API token, register at [developer portal](https://developers.example.com).

                ## Endpoints

                ---

                ### List Items

                `GET /items`

                Retrieves a paginated list of items.

                **Query Parameters:**

                | Parameter | Type   | Required | Description          |
                |-----------|--------|----------|----------------------|
                | page      | number | No       | Page number (1-based)|
                | limit     | number | No       | Items per page (max 100) |
                | sort      | string | No       | Sort field           |

                **Response:**

                ```json
                {
                  "data": [
                    {
                      "id": "item_123",
                      "name": "Example Item",
                      "created_at": "2024-01-15T10:30:00Z"
                    }
                  ],
                  "pagination": {
                    "page": 1,
                    "limit": 20,
                    "total": 150
                  }
                }
                ```

                ---

                ### Create Item

                `POST /items`

                Creates a new item.

                **Request Body:**

                ```json
                {
                  "name": "New Item",
                  "description": "A description of the item",
                  "tags": ["tag1", "tag2"]
                }
                ```

                **Response:** `201 Created`

                ```json
                {
                  "id": "item_456",
                  "name": "New Item",
                  "status": "created"
                }
                ```

                ---

                ### Get Item by ID

                `GET /items/{id}`

                **Path Parameters:**

                | Parameter | Type   | Description  |
                |-----------|--------|--------------|
                | id        | string | Item ID      |

                **Response:** `200 OK`

                ---

                ### Update Item

                `PATCH /items/{id}`

                **Request Body:**

                ```json
                {
                  "name": "Updated Name",
                  "description": "Updated description"
                }
                ```

                **Response:** `200 OK`

                ---

                ### Delete Item

                `DELETE /items/{id}`

                **Response:** `204 No Content`

                ## Error Codes

                | Code | HTTP Status | Description          |
                |------|-------------|----------------------|
                | 400  | Bad Request | Invalid input        |
                | 401  | Unauthorized| Missing/invalid token|
                | 403  | Forbidden   | Insufficient permissions |
                | 404  | Not Found   | Resource not found   |
                | 429  | Too Many Requests | Rate limit hit |
                | 500  | Server Error| Internal server error|

                ## Rate Limiting

                - **100 requests per minute** per API token
                - Rate limit headers included in each response:
                  - `X-RateLimit-Limit`
                  - `X-RateLimit-Remaining`
                  - `X-RateLimit-Reset`

                ## SDKs

                | Language | Repository                                        |
                |----------|---------------------------------------------------|
                | Java     | [github.com/example/api-java](https://github.com)  |
                | Python   | [github.com/example/api-python](https://github.com) |
                | Node.js  | [github.com/example/api-node](https://github.com)   |

                ## Changelog

                ### v1.0.0 (2024-01-15)

                - Initial release
                - CRUD operations for items
                - Pagination support
                """;
    }
}
