package com.readmeeditor.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.readmeeditor.config.AppConfig;
import com.readmeeditor.config.RedisConfig;
import com.readmeeditor.model.*;
import com.readmeeditor.repository.ReadmeRepository;
import com.readmeeditor.repository.UserRepository;
import com.readmeeditor.service.*;
import com.readmeeditor.util.PasswordUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Embedded Web Server for README Editor.
 * Serves the Notion-like web interface and handles REST API requests.
 */
public class WebServer {
    private static final Logger LOG = LoggerFactory.getLogger(WebServer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final int port;
    private HttpServer server;

    private final UserRepository userRepository;
    private final ReadmeRepository readmeRepository;
    private final UserService userService;
    private final VersionService versionService;
    private final ReadmeService readmeService;
    private final TemplateService templateService;
    private final SearchService searchService;
    private final MarkdownService markdownService;

    public WebServer() {
        this.port = AppConfig.getInstance().getServerPort();
        
        // Initialize backend layers
        this.userRepository = new UserRepository();
        this.readmeRepository = new ReadmeRepository();
        this.userService = new UserService(userRepository);
        this.versionService = new VersionService(readmeRepository);
        this.readmeService = new ReadmeService(readmeRepository, versionService);
        this.templateService = new TemplateService();
        this.searchService = new SearchService(readmeRepository);
        this.markdownService = new MarkdownService();
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new StaticHandler());
            server.createContext("/api", new ApiHandler());
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            LOG.info("Web Server started successfully on port {}!", port);
            System.out.println("=================================================");
            System.out.println("🚀 README Editor Web Server is running!");
            System.out.println("👉 Access the Notion-like UI at http://localhost:" + port);
            System.out.println("=================================================");
        } catch (IOException e) {
            LOG.error("Failed to start web server on port {}", port, e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            LOG.info("Web Server stopped.");
        }
    }

    /**
     * Handler for serving HTML, CSS, and JS files.
     */
    private class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }

            // Read file from classpath resources/web/
            String resourcePath = "web" + path;
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    sendError(exchange, 404, "File Not Found: " + path);
                    return;
                }

                byte[] content = is.readAllBytes();
                String contentType = getContentType(path);
                exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }
            } catch (Exception e) {
                LOG.error("Error serving static file: {}", path, e);
                sendError(exchange, 500, "Internal Server Error");
            }
        }

        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "application/javascript";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".svg")) return "image/svg+xml";
            return "text/plain";
        }
    }

    /**
     * Handler for REST API requests.
     */
    private class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Enable CORS
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-User-Id");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            LOG.debug("API Request: {} {}", method, path);

            try {
                if (path.equals("/api/auth/register") && method.equals("POST")) {
                    handleRegister(exchange);
                } else if (path.equals("/api/auth/login") && method.equals("POST")) {
                    handleLogin(exchange);
                } else if (path.equals("/api/health") && method.equals("GET")) {
                    handleHealthCheck(exchange);
                } else if (path.equals("/api/workspaces") && method.equals("GET")) {
                    handleListWorkspaces(exchange);
                } else if (path.equals("/api/workspaces") && method.equals("POST")) {
                    handleCreateWorkspace(exchange);
                } else if (path.startsWith("/api/workspaces/") && method.equals("DELETE")) {
                    handleDeleteWorkspace(exchange);
                } else if (path.equals("/api/documents") && method.equals("GET")) {
                    handleListDocuments(exchange);
                } else if (path.equals("/api/documents") && method.equals("POST")) {
                    handleCreateDocument(exchange);
                } else if (path.startsWith("/api/documents/") && path.endsWith("/presence") && method.equals("POST")) {
                    handleDocumentPresence(exchange);
                } else if (path.equals("/api/assets") && method.equals("POST")) {
                    handleUploadAsset(exchange);
                } else if (path.startsWith("/api/assets/") && method.equals("GET")) {
                    handleServeAsset(exchange);
                } else if (path.startsWith("/api/documents/") && path.endsWith("/versions") && method.equals("GET")) {
                    handleGetVersions(exchange);
                } else if (path.matches("/api/documents/[^/]+/versions/[0-9]+/restore") && method.equals("POST")) {
                    handleRestoreVersion(exchange);
                } else if (path.matches("/api/documents/[^/]+/versions/[0-9]+") && method.equals("GET")) {
                    handleGetVersion(exchange);
                } else if (path.startsWith("/api/documents/") && path.contains("/compare") && method.equals("GET")) {
                    handleCompareVersions(exchange);
                } else if (path.startsWith("/api/documents/") && method.equals("GET")) {
                    handleGetDocument(exchange);
                } else if (path.startsWith("/api/documents/") && method.equals("PUT")) {
                    handleUpdateDocument(exchange);
                } else if (path.startsWith("/api/documents/") && method.equals("DELETE")) {
                    handleDeleteDocument(exchange);
                } else if (path.equals("/api/templates") && method.equals("GET")) {
                    handleListTemplates(exchange);
                } else if (path.startsWith("/api/templates/") && method.equals("GET")) {
                    handleGetTemplate(exchange);
                } else if (path.equals("/api/search") && method.equals("GET")) {
                    handleSearch(exchange);
                } else if (path.equals("/api/markdown") && method.equals("POST")) {
                    handleRenderMarkdown(exchange);
                } else {
                    sendError(exchange, 404, "API endpoint not found");
                }
            } catch (IllegalArgumentException e) {
                LOG.warn("Bad API request: {}", e.getMessage());
                sendError(exchange, 400, e.getMessage());
            } catch (Exception e) {
                LOG.error("API error", e);
                sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }

        // ==================== API Handlers ====================

        private void handleRegister(HttpExchange exchange) throws IOException {
            Map<String, String> body = MAPPER.readValue(exchange.getRequestBody(), Map.class);
            String username = body.get("username");
            String password = body.get("password");
            String email = body.get("email");

            User user = userService.registerUser(username, password, email);
            sendJson(exchange, 200, user);
        }

        private void handleLogin(HttpExchange exchange) throws IOException {
            Map<String, String> body = MAPPER.readValue(exchange.getRequestBody(), Map.class);
            String username = body.get("username");
            String password = body.get("password");

            User user = userService.authenticate(username, password);
            sendJson(exchange, 200, user);
        }

        private void handleHealthCheck(HttpExchange exchange) throws IOException {
            Map<String, Object> health = new HashMap<>();
            boolean isRedisUp = false;
            try (redis.clients.jedis.Jedis jedis = RedisConfig.getInstance().getConnection()) {
                String ping = jedis.ping();
                isRedisUp = "PONG".equalsIgnoreCase(ping);
            } catch (Exception e) {
                LOG.error("Health check failed: Redis is down", e);
            }

            health.put("status", isRedisUp ? "UP" : "DOWN");
            health.put("redis", isRedisUp ? "UP" : "DOWN");
            health.put("timestamp", LocalDateTime.now().toString());

            // Get JVM memory info
            Runtime runtime = Runtime.getRuntime();
            health.put("jvmFreeMemoryBytes", runtime.freeMemory());
            health.put("jvmMaxMemoryBytes", runtime.maxMemory());
            health.put("jvmTotalMemoryBytes", runtime.totalMemory());

            int status = isRedisUp ? 200 : 500;
            sendJson(exchange, status, health);
        }

        private void handleListWorkspaces(HttpExchange exchange) throws IOException {
            String userId = getUserId(exchange);
            List<Workspace> list = readmeService.findWorkspacesByUserId(userId);
            if (list.isEmpty()) {
                // Create a default workspace
                Workspace ws = readmeService.createWorkspace("Personal Workspace", userId);
                list = List.of(ws);

                // Migrate existing documents to this default workspace
                List<ReadmeDocument> existingDocs = readmeService.findDocumentsByUserId(userId);
                for (ReadmeDocument doc : existingDocs) {
                    if (doc.getWorkspaceId() == null || doc.getWorkspaceId().isEmpty()) {
                        doc.setWorkspaceId(ws.getId());
                        readmeService.saveDocument(doc);
                    }
                }
            }
            sendJson(exchange, 200, list);
        }

        private void handleCreateWorkspace(HttpExchange exchange) throws IOException {
            String userId = getUserId(exchange);
            Map<String, String> body = MAPPER.readValue(exchange.getRequestBody(), Map.class);
            String name = body.getOrDefault("name", "New Workspace");

            Workspace ws = readmeService.createWorkspace(name, userId);
            sendJson(exchange, 200, ws);
        }

        private void handleDeleteWorkspace(HttpExchange exchange) throws IOException {
            String id = extractIdFromPath(exchange.getRequestURI().getPath(), "/api/workspaces/");
            readmeService.deleteWorkspace(id);
            sendJson(exchange, 200, Map.of("success", true));
        }

        private void handleListDocuments(HttpExchange exchange) throws IOException {
            String userId = getUserId(exchange);
            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
            String workspaceId = queryParams.get("workspaceId");

            List<ReadmeDocument> docs;
            if (workspaceId != null && !workspaceId.isEmpty()) {
                docs = readmeService.findDocumentsByWorkspaceId(workspaceId);
            } else {
                docs = readmeService.findDocumentsByUserId(userId);
            }
            sendJson(exchange, 200, docs);
        }

        private void handleCreateDocument(HttpExchange exchange) throws IOException {
            String userId = getUserId(exchange);
            Map<String, String> body = MAPPER.readValue(exchange.getRequestBody(), Map.class);
            String title = body.getOrDefault("title", "Untitled");
            String content = body.getOrDefault("content", "");
            String workspaceId = body.get("workspaceId");

            if (workspaceId == null || workspaceId.isEmpty()) {
                List<Workspace> workspaces = readmeService.findWorkspacesByUserId(userId);
                if (workspaces.isEmpty()) {
                    Workspace ws = readmeService.createWorkspace("Personal Workspace", userId);
                    workspaceId = ws.getId();
                } else {
                    workspaceId = workspaces.get(0).getId();
                }
            }

            ReadmeDocument doc = readmeService.createDocument(title, content, userId, workspaceId);
            sendJson(exchange, 200, doc);
        }

        private void handleGetDocument(HttpExchange exchange) throws IOException {
            String id = extractIdFromPath(exchange.getRequestURI().getPath(), "/api/documents/");
            Optional<ReadmeDocument> doc = readmeService.findDocumentById(id);
            if (doc.isEmpty()) {
                sendError(exchange, 404, "Document not found");
                return;
            }
            sendJson(exchange, 200, doc.get());
        }

        private void handleUpdateDocument(HttpExchange exchange) throws IOException {
            String id = extractIdFromPath(exchange.getRequestURI().getPath(), "/api/documents/");
            Map<String, Object> body = MAPPER.readValue(exchange.getRequestBody(), Map.class);

            Optional<ReadmeDocument> optDoc = readmeService.findDocumentById(id);
            if (optDoc.isEmpty()) {
                sendError(exchange, 404, "Document not found");
                return;
            }

            ReadmeDocument doc = optDoc.get();
            if (body.containsKey("title")) {
                doc.setTitle((String) body.get("title"));
            }
            if (body.containsKey("content")) {
                doc.setContent((String) body.get("content"));
            }
            if (body.containsKey("tagIds")) {
                doc.setTagIds((List<String>) body.get("tagIds"));
            }
            doc.setUpdatedAt(LocalDateTime.now());

            boolean createVersion = Boolean.parseBoolean(String.valueOf(body.getOrDefault("createVersion", "false")));
            String commitMessage = (String) body.getOrDefault("commitMessage", "Auto-save");

            // Save the document
            if (createVersion) {
                doc = readmeService.saveWithVersion(doc, doc.getCreatedByUserId(), commitMessage);
            } else {
                readmeService.saveDocument(doc);
            }

            sendJson(exchange, 200, doc);
        }

        private void handleDeleteDocument(HttpExchange exchange) throws IOException {
            String id = extractIdFromPath(exchange.getRequestURI().getPath(), "/api/documents/");
            readmeService.deleteDocument(id);
            sendJson(exchange, 200, Map.of("success", true));
        }

        private void handleGetVersions(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            // Path: /api/documents/{id}/versions
            String docId = path.substring("/api/documents/".length(), path.indexOf("/versions"));
            List<ReadmeVersion> versions = versionService.getVersions(docId);
            sendJson(exchange, 200, versions);
        }

        private void handleGetVersion(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            // Path: /api/documents/{id}/versions/{v}
            String docId = path.substring("/api/documents/".length(), path.indexOf("/versions/"));
            int versionNum = Integer.parseInt(path.substring(path.indexOf("/versions/") + "/versions/".length()));

            Optional<ReadmeVersion> version = versionService.getVersion(docId, versionNum);
            if (version.isEmpty()) {
                sendError(exchange, 404, "Version not found");
                return;
            }
            sendJson(exchange, 200, version.get());
        }

        private void handleRestoreVersion(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            // Path: /api/documents/{id}/versions/{v}/restore
            String docId = path.substring("/api/documents/".length(), path.indexOf("/versions/"));
            String subStr = path.substring(path.indexOf("/versions/") + "/versions/".length());
            int versionNum = Integer.parseInt(subStr.substring(0, subStr.indexOf("/restore")));

            String userId = getUserId(exchange);
            ReadmeDocument doc = versionService.restoreVersion(docId, versionNum, userId);
            sendJson(exchange, 200, doc);
        }

        private void handleCompareVersions(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            // Path: /api/documents/{id}/compare?v1=X&v2=Y
            String docId = path.substring("/api/documents/".length(), path.indexOf("/compare"));
            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            int v1 = Integer.parseInt(query.getOrDefault("v1", "1"));
            int v2 = Integer.parseInt(query.getOrDefault("v2", "2"));

            String diff = versionService.compareVersions(docId, v1, v2);
            sendJson(exchange, 200, Map.of("diff", diff));
        }

        private void handleListTemplates(HttpExchange exchange) throws IOException {
            Map<String, String> templates = templateService.getBuiltInTemplates();
            sendJson(exchange, 200, templates.keySet());
        }

        private void handleGetTemplate(HttpExchange exchange) throws IOException {
            String name = extractIdFromPath(exchange.getRequestURI().getPath(), "/api/templates/");
            String decodedName = URLDecoder.decode(name, StandardCharsets.UTF_8);
            String content = templateService.getTemplate(decodedName);
            if (content == null) {
                sendError(exchange, 404, "Template not found");
                return;
            }
            sendJson(exchange, 200, Map.of("name", decodedName, "content", content));
        }

        private void handleSearch(HttpExchange exchange) throws IOException {
            String userId = getUserId(exchange);
            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
            String query = queryParams.getOrDefault("q", "");

            // Using full-text search or simple tag filtering
            List<ReadmeDocument> allDocs = readmeService.findDocumentsByUserId(userId);
            List<ReadmeDocument> results;
            if (query.isEmpty()) {
                results = allDocs;
            } else {
                String lower = query.toLowerCase();
                results = allDocs.stream()
                        .filter(d -> (d.getTitle() != null && d.getTitle().toLowerCase().contains(lower)) 
                                || (d.getContent() != null && d.getContent().toLowerCase().contains(lower)))
                        .collect(Collectors.toList());
            }

            sendJson(exchange, 200, results);
        }

        private void handleRenderMarkdown(HttpExchange exchange) throws IOException {
            Map<String, String> body = MAPPER.readValue(exchange.getRequestBody(), Map.class);
            String markdown = body.getOrDefault("markdown", "");
            String html = markdownService.renderToHtml(markdown);
            sendJson(exchange, 200, Map.of("html", html));
        }

        private void handleDocumentPresence(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            // Path: /api/documents/{id}/presence
            String docId = path.substring("/api/documents/".length(), path.indexOf("/presence"));
            String userId = getUserId(exchange);

            Optional<User> userOpt = userRepository.findById(userId);
            String username = userOpt.map(User::getUsername).orElse("Anonymous");

            long now = System.currentTimeMillis();
            try (redis.clients.jedis.Jedis jedis = RedisConfig.getInstance().getConnection()) {
                String key = "readme:" + docId + ":presence";
                // Add member with current timestamp
                jedis.zadd(key, now, userId + ":" + username);
                // Remove users inactive for more than 10 seconds
                jedis.zremrangeByScore(key, 0, now - 10000);

                // Fetch current active users
                List<String> activeMembers = jedis.zrange(key, 0, -1);
                List<Map<String, String>> usersList = new ArrayList<>();
                for (String member : activeMembers) {
                    int colonIdx = member.indexOf(":");
                    if (colonIdx != -1) {
                        String uid = member.substring(0, colonIdx);
                        String uname = member.substring(colonIdx + 1);
                        usersList.add(Map.of("id", uid, "username", uname));
                    }
                }
                sendJson(exchange, 200, usersList);
            }
        }

        private void handleUploadAsset(HttpExchange exchange) throws IOException {
            Map<String, String> body = MAPPER.readValue(exchange.getRequestBody(), Map.class);
            String filename = body.get("filename");
            String base64Data = body.get("base64");

            if (filename == null || base64Data == null) {
                sendError(exchange, 400, "Filename or base64 data is missing");
                return;
            }

            // Strip the base64 prefix if present (e.g. data:image/png;base64,)
            if (base64Data.contains(",")) {
                base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
            }

            byte[] fileBytes = Base64.getDecoder().decode(base64Data);

            // Clean up filename and ensure it has extension
            String cleanName = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String ext = "";
            int extIdx = cleanName.lastIndexOf(".");
            if (extIdx != -1) {
                ext = cleanName.substring(extIdx);
                cleanName = cleanName.substring(0, extIdx);
            }
            String uniqueName = cleanName + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;

            // Ensure uploads directory exists
            Path uploadDir = Paths.get("uploads");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            Path filePath = uploadDir.resolve(uniqueName);
            Files.write(filePath, fileBytes);

            LOG.info("Asset uploaded successfully: {} -> {}", filename, uniqueName);
            sendJson(exchange, 200, Map.of(
                "filename", uniqueName,
                "url", "/api/assets/" + uniqueName
            ));
        }

        private void handleServeAsset(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            // Path: /api/assets/{filename}
            String filename = extractIdFromPath(path, "/api/assets/");
            Path filePath = Paths.get("uploads").resolve(filename);

            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                sendError(exchange, 404, "Asset not found: " + filename);
                return;
            }

            byte[] bytes = Files.readAllBytes(filePath);
            String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
            String contentType = "application/octet-stream";
            if (ext.equals("png")) contentType = "image/png";
            else if (ext.equals("jpg") || ext.equals("jpeg")) contentType = "image/jpeg";
            else if (ext.equals("gif")) contentType = "image/gif";
            else if (ext.equals("svg")) contentType = "image/svg+xml";

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        // ==================== Helper Methods ====================

        private String getUserId(HttpExchange exchange) {
            String userId = exchange.getRequestHeaders().getFirst("X-User-Id");
            if (userId == null || userId.isEmpty()) {
                throw new IllegalArgumentException("User ID missing. Please login first.");
            }
            return userId;
        }

        private String extractIdFromPath(String path, String prefix) {
            String id = path.substring(prefix.length());
            if (id.contains("/")) {
                id = id.substring(0, id.indexOf("/"));
            }
            return id;
        }

        private Map<String, String> parseQueryParams(String query) {
            if (query == null || query.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, String> map = new HashMap<>();
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length > 1) {
                    map.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), 
                            URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
                } else if (pair.length > 0) {
                    map.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), "");
                }
            }
            return map;
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, Object data) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(data);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        sendJson(exchange, statusCode, Map.of("error", message));
    }
}
