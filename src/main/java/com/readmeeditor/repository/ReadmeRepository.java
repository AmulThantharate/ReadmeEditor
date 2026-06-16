package com.readmeeditor.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.readmeeditor.config.RedisConfig;
import com.readmeeditor.model.ReadmeDocument;
import com.readmeeditor.model.ReadmeVersion;
import com.readmeeditor.model.Tag;
import com.readmeeditor.model.Workspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Repository for managing README documents and their versions in Redis.
 * <p>
 * Storage schema:
 * <ul>
 *   <li>{@code readme:{id}} — Hash containing document fields</li>
 *   <li>{@code readme:{id}:versions} — Sorted set of version metadata JSON</li>
 *   <li>{@code readme:all} — Set of all README document IDs</li>
 *   <li>{@code tags:{id}} — String tag name</li>
 *   <li>{@code tags:name:{name}} — String mapping tag name to ID</li>
 *   <li>{@code tags:all} — Set of all tag IDs</li>
 *   <li>{@code readme:tag:{tagId}} — Set of document IDs with that tag</li>
 * </ul>
 */
public class ReadmeRepository {
    private static final Logger LOG = LoggerFactory.getLogger(ReadmeRepository.class);

    private static final String README_PREFIX = "readme:";
    private static final String ALL_README_KEY = "readme:all";
    private static final String VERSIONS_SUFFIX = ":versions";
    private static final String TAG_PREFIX = "tags:";
    private static final String TAG_NAME_INDEX = "tags:name:";
    private static final String ALL_TAGS_KEY = "tags:all";
    private static final String README_TAG_PREFIX = "readme:tag:";

    private final RedisConfig redisConfig;
    private final ObjectMapper objectMapper;

    public ReadmeRepository() {
        this.redisConfig = RedisConfig.getInstance();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    ReadmeRepository(RedisConfig redisConfig, ObjectMapper objectMapper) {
        this.redisConfig = redisConfig;
        this.objectMapper = objectMapper;
    }

    // ==================== Document CRUD ====================

    /**
     * Saves a README document to Redis.
     *
     * @param doc the document to save
     * @return the saved document
     */
    public ReadmeDocument saveDocument(ReadmeDocument doc) {
        Objects.requireNonNull(doc, "Document must not be null");
        Objects.requireNonNull(doc.getId(), "Document ID must not be null");

        try (Jedis jedis = redisConfig.getConnection()) {
            String key = README_PREFIX + doc.getId();
            Map<String, String> hash = new HashMap<>();
            hash.put("title", doc.getTitle() != null ? doc.getTitle() : "");
            hash.put("content", doc.getContent() != null ? doc.getContent() : "");
            hash.put("createdByUserId", doc.getCreatedByUserId() != null ? doc.getCreatedByUserId() : "");
            hash.put("workspaceId", doc.getWorkspaceId() != null ? doc.getWorkspaceId() : "");
            hash.put("createdAt", doc.getCreatedAt().toString());
            hash.put("updatedAt", doc.getUpdatedAt().toString());
            hash.put("currentVersion", String.valueOf(doc.getCurrentVersion()));

            String tagsJson = doc.getTagIds() != null
                    ? String.join(",", doc.getTagIds())
                    : "";
            hash.put("tagIds", tagsJson);

            jedis.hset(key, hash);
            jedis.sadd(ALL_README_KEY, doc.getId());

            LOG.debug("Document saved: {} (v{})", doc.getTitle(), doc.getCurrentVersion());
            return doc;
        }
    }

    /**
     * Finds a README document by its ID.
     *
     * @param id the document ID
     * @return an Optional containing the document, or empty if not found
     */
    public Optional<ReadmeDocument> findDocumentById(String id) {
        Objects.requireNonNull(id, "Document ID must not be null");

        try (Jedis jedis = redisConfig.getConnection()) {
            String key = README_PREFIX + id;
            Map<String, String> hash = jedis.hgetAll(key);
            if (hash.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(mapToDocument(id, hash));
        }
    }

    /**
     * Returns all README documents, ordered by most recently updated first.
     */
    public List<ReadmeDocument> findAllDocuments() {
        try (Jedis jedis = redisConfig.getConnection()) {
            var ids = jedis.smembers(ALL_README_KEY);
            List<ReadmeDocument> docs = new ArrayList<>();
            for (String id : ids) {
                findDocumentById(id).ifPresent(docs::add);
            }
            docs.sort(Comparator.comparing(ReadmeDocument::getUpdatedAt).reversed());
            return docs;
        }
    }

    /**
     * Finds documents created by a specific user.
     */
    public List<ReadmeDocument> findDocumentsByUserId(String userId) {
        return findAllDocuments().stream()
                .filter(doc -> userId.equals(doc.getCreatedByUserId()))
                .collect(Collectors.toList());
    }

    /**
     * Returns the most recently updated documents.
     *
     * @param limit the maximum number of documents to return
     */
    public List<ReadmeDocument> findRecentDocuments(int limit) {
        List<ReadmeDocument> all = findAllDocuments();
        return all.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * Finds documents that match a search query in their title or content.
     */
    public List<ReadmeDocument> searchDocuments(String query) {
        Objects.requireNonNull(query, "Search query must not be null");
        String lowerQuery = query.toLowerCase();
        return findAllDocuments().stream()
                .filter(doc -> doc.getTitle() != null
                        && doc.getTitle().toLowerCase().contains(lowerQuery)
                        || doc.getContent() != null
                        && doc.getContent().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }

    /**
     * Deletes a document and all its versions from Redis.
     */
    public void deleteDocument(String id) {
        Objects.requireNonNull(id, "Document ID must not be null");

        try (Jedis jedis = redisConfig.getConnection()) {
            Optional<ReadmeDocument> doc = findDocumentById(id);
            if (doc.isPresent()) {
                // Remove tag associations
                for (String tagId : doc.get().getTagIds()) {
                    jedis.srem(README_TAG_PREFIX + tagId, id);
                }
                // Delete versions and document
                jedis.del(README_PREFIX + id + VERSIONS_SUFFIX);
                jedis.del(README_PREFIX + id);
                jedis.srem(ALL_README_KEY, id);
                LOG.info("Document deleted: {} ({})", doc.get().getTitle(), id);
            }
        }
    }

    /**
     * Counts total documents.
     */
    public long countDocuments() {
        try (Jedis jedis = redisConfig.getConnection()) {
            return jedis.scard(ALL_README_KEY);
        }
    }

    // ==================== Version Management ====================

    /**
     * Saves a version of a document.
     *
     * @param version the version to save
     */
    public void saveVersion(ReadmeVersion version) {
        Objects.requireNonNull(version, "Version must not be null");

        try (Jedis jedis = redisConfig.getConnection()) {
            String key = README_PREFIX + version.getDocumentId() + VERSIONS_SUFFIX;
            try {
                String versionJson = objectMapper.writeValueAsString(version);
                jedis.zadd(key, version.getVersionNumber(), versionJson);
                LOG.debug("Version {} saved for document: {}", version.getVersionNumber(), version.getDocumentId());
            } catch (JsonProcessingException e) {
                LOG.error("Failed to serialize version", e);
                throw new RuntimeException("Failed to serialize version", e);
            }
        }
    }

    /**
     * Returns all versions of a document, sorted by version number descending.
     */
    public List<ReadmeVersion> getVersions(String documentId) {
        Objects.requireNonNull(documentId, "Document ID must not be null");

        try (Jedis jedis = redisConfig.getConnection()) {
            String key = README_PREFIX + documentId + VERSIONS_SUFFIX;
            var versionJsons = jedis.zrevrangeByScore(key, "+inf", "-inf");
            List<ReadmeVersion> versions = new ArrayList<>();
            for (String json : versionJsons) {
                try {
                    versions.add(objectMapper.readValue(json, ReadmeVersion.class));
                } catch (JsonProcessingException e) {
                    LOG.error("Failed to deserialize version", e);
                }
            }
            return versions;
        }
    }

    /**
     * Returns a specific version of a document.
     */
    public Optional<ReadmeVersion> getVersion(String documentId, int versionNumber) {
        return getVersions(documentId).stream()
                .filter(v -> v.getVersionNumber() == versionNumber)
                .findFirst();
    }

    /**
     * Returns the total number of versions across all documents.
     */
    public long countTotalVersions() {
        long count = 0;
        for (ReadmeDocument doc : findAllDocuments()) {
            count += getVersions(doc.getId()).size();
        }
        return count;
    }

    // ==================== Tag Management ====================

    /**
     * Creates or updates a tag.
     *
     * @return the saved tag
     */
    public Tag saveTag(Tag tag) {
        Objects.requireNonNull(tag, "Tag must not be null");

        try (Jedis jedis = redisConfig.getConnection()) {
            jedis.set(TAG_PREFIX + tag.getId(), tag.getName());
            jedis.set(TAG_NAME_INDEX + tag.getName().toLowerCase(), tag.getId());
            jedis.sadd(ALL_TAGS_KEY, tag.getId());
            LOG.debug("Tag saved: {}", tag.getName());
            return tag;
        }
    }

    /**
     * Finds a tag by ID.
     */
    public Optional<Tag> findTagById(String id) {
        try (Jedis jedis = redisConfig.getConnection()) {
            String name = jedis.get(TAG_PREFIX + id);
            if (name == null) {
                return Optional.empty();
            }
            return Optional.of(new Tag(id, name));
        }
    }

    /**
     * Finds a tag by name.
     */
    public Optional<Tag> findTagByName(String name) {
        try (Jedis jedis = redisConfig.getConnection()) {
            String id = jedis.get(TAG_NAME_INDEX + name.toLowerCase());
            if (id == null) {
                return Optional.empty();
            }
            return findTagById(id);
        }
    }

    /**
     * Returns all tags.
     */
    public List<Tag> findAllTags() {
        try (Jedis jedis = redisConfig.getConnection()) {
            var ids = jedis.smembers(ALL_TAGS_KEY);
            List<Tag> tags = new ArrayList<>();
            for (String id : ids) {
                findTagById(id).ifPresent(tags::add);
            }
            tags.sort(Comparator.comparing(Tag::getName));
            return tags;
        }
    }

    /**
     * Tags a document with a specific tag.
     */
    public void addTagToDocument(String documentId, String tagId) {
        try (Jedis jedis = redisConfig.getConnection()) {
            jedis.sadd(README_TAG_PREFIX + tagId, documentId);
            findDocumentById(documentId).ifPresent(doc -> {
                doc.addTagId(tagId);
                saveDocument(doc);
            });
        }
    }

    /**
     * Removes a tag from a document.
     */
    public void removeTagFromDocument(String documentId, String tagId) {
        try (Jedis jedis = redisConfig.getConnection()) {
            jedis.srem(README_TAG_PREFIX + tagId, documentId);
            findDocumentById(documentId).ifPresent(doc -> {
                doc.removeTagId(tagId);
                saveDocument(doc);
            });
        }
    }

    /**
     * Finds documents tagged with a specific tag name.
     */
    public List<ReadmeDocument> findDocumentsByTagName(String tagName) {
        Optional<Tag> tag = findTagByName(tagName);
        if (tag.isEmpty()) {
            return Collections.emptyList();
        }
        try (Jedis jedis = redisConfig.getConnection()) {
            var docIds = jedis.smembers(README_TAG_PREFIX + tag.get().getId());
            return docIds.stream()
                    .map(this::findDocumentById)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Deletes a tag and removes it from all associated documents.
     */
    public void deleteTag(String tagId) {
        Objects.requireNonNull(tagId, "Tag ID must not be null");

        try (Jedis jedis = redisConfig.getConnection()) {
            Optional<Tag> tag = findTagById(tagId);
            if (tag.isPresent()) {
                // Remove from all documents
                var docIds = jedis.smembers(README_TAG_PREFIX + tagId);
                for (String docId : docIds) {
                    removeTagFromDocument(docId, tagId);
                }
                jedis.del(TAG_PREFIX + tagId);
                jedis.del(TAG_NAME_INDEX + tag.get().getName().toLowerCase());
                jedis.del(README_TAG_PREFIX + tagId);
                jedis.srem(ALL_TAGS_KEY, tagId);
                LOG.info("Tag deleted: {}", tag.get().getName());
            }
        }
    }

    // ==================== Workspace Management ====================

    /**
     * Saves a workspace to Redis.
     */
    public Workspace saveWorkspace(Workspace ws) {
        Objects.requireNonNull(ws, "Workspace must not be null");
        Objects.requireNonNull(ws.getId(), "Workspace ID must not be null");

        try (Jedis jedis = redisConfig.getConnection()) {
            String key = "workspace:" + ws.getId();
            Map<String, String> hash = new HashMap<>();
            hash.put("name", ws.getName() != null ? ws.getName() : "");
            hash.put("createdByUserId", ws.getCreatedByUserId() != null ? ws.getCreatedByUserId() : "");
            hash.put("createdAt", ws.getCreatedAt().toString());

            jedis.hset(key, hash);
            if (ws.getCreatedByUserId() != null) {
                jedis.sadd("user:" + ws.getCreatedByUserId() + ":workspaces", ws.getId());
            }
            LOG.debug("Workspace saved: {}", ws.getName());
            return ws;
        }
    }

    /**
     * Finds a workspace by ID.
     */
    public Optional<Workspace> findWorkspaceById(String id) {
        Objects.requireNonNull(id, "Workspace ID must not be null");

        try (Jedis jedis = redisConfig.getConnection()) {
            String key = "workspace:" + id;
            Map<String, String> hash = jedis.hgetAll(key);
            if (hash.isEmpty()) {
                return Optional.empty();
            }
            Workspace ws = new Workspace();
            ws.setId(id);
            ws.setName(hash.getOrDefault("name", ""));
            ws.setCreatedByUserId(hash.getOrDefault("createdByUserId", ""));
            ws.setCreatedAt(LocalDateTime.parse(hash.getOrDefault("createdAt", LocalDateTime.now().toString())));
            return Optional.of(ws);
        }
    }

    /**
     * Finds all workspaces belonging to a user.
     */
    public List<Workspace> findWorkspacesByUserId(String userId) {
        Objects.requireNonNull(userId, "User ID must not be null");

        try (Jedis jedis = redisConfig.getConnection()) {
            var ids = jedis.smembers("user:" + userId + ":workspaces");
            List<Workspace> list = new ArrayList<>();
            for (String id : ids) {
                findWorkspaceById(id).ifPresent(list::add);
            }
            list.sort(Comparator.comparing(Workspace::getCreatedAt));
            return list;
        }
    }

    /**
     * Deletes a workspace and its documents.
     */
    public void deleteWorkspace(String workspaceId) {
        Objects.requireNonNull(workspaceId, "Workspace ID must not be null");

        try (Jedis jedis = redisConfig.getConnection()) {
            Optional<Workspace> wsOpt = findWorkspaceById(workspaceId);
            if (wsOpt.isPresent()) {
                Workspace ws = wsOpt.get();
                // Find all documents in this workspace and delete them
                List<ReadmeDocument> docs = findDocumentsByWorkspaceId(workspaceId);
                for (ReadmeDocument doc : docs) {
                    deleteDocument(doc.getId());
                }
                
                // Delete workspace hash
                jedis.del("workspace:" + workspaceId);
                
                // Remove from user's workspace set
                if (ws.getCreatedByUserId() != null) {
                    jedis.srem("user:" + ws.getCreatedByUserId() + ":workspaces", workspaceId);
                }
                LOG.info("Workspace deleted: {}", ws.getName());
            }
        }
    }

    /**
     * Finds documents belonging to a workspace.
     */
    public List<ReadmeDocument> findDocumentsByWorkspaceId(String workspaceId) {
        Objects.requireNonNull(workspaceId, "Workspace ID must not be null");
        return findAllDocuments().stream()
                .filter(doc -> workspaceId.equals(doc.getWorkspaceId()))
                .collect(Collectors.toList());
    }

    // ==================== Mapping Helpers ====================

    /**
     * Maps a Redis hash to a ReadmeDocument object.
     */
    private ReadmeDocument mapToDocument(String id, Map<String, String> hash) {
        ReadmeDocument doc = new ReadmeDocument();
        doc.setId(id);
        doc.setTitle(hash.getOrDefault("title", ""));
        doc.setContent(hash.getOrDefault("content", ""));
        doc.setCreatedByUserId(hash.getOrDefault("createdByUserId", ""));
        doc.setWorkspaceId(hash.getOrDefault("workspaceId", ""));

        String tagIdsStr = hash.getOrDefault("tagIds", "");
        if (!tagIdsStr.isEmpty()) {
            doc.setTagIds(new ArrayList<>(Arrays.asList(tagIdsStr.split(","))));
        }

        doc.setCreatedAt(LocalDateTime.parse(hash.getOrDefault("createdAt", LocalDateTime.now().toString())));
        doc.setUpdatedAt(LocalDateTime.parse(hash.getOrDefault("updatedAt", LocalDateTime.now().toString())));
        doc.setCurrentVersion(Integer.parseInt(hash.getOrDefault("currentVersion", "0")));

        return doc;
    }
}
