package com.readmeeditor.view;

import com.readmeeditor.controller.*;
import com.readmeeditor.model.ReadmeDocument;
import com.readmeeditor.model.User;
import com.readmeeditor.service.ExportImportService;
import com.readmeeditor.service.MarkdownService;
import com.readmeeditor.service.TemplateService;
import com.readmeeditor.util.AutoSaveManager;
import com.readmeeditor.util.ThemeManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Main editor view with a sidebar, toolbar, split-pane Markdown editor/preview,
 * and access to version history, templates, search, and dashboard.
 * <p>
 * This is the primary workspace for creating and editing README documents.
 */
public class EditorView {
    private static final Logger LOG = LoggerFactory.getLogger(EditorView.class);

    private final Stage primaryStage;
    private final User currentUser;
    private final LoginController loginController;
    private final ReadmeController readmeController;
    private final DashboardController dashboardController;
    private final VersionController versionController;
    private final TemplateController templateController;
    private final SearchController searchController;
    private final MarkdownService markdownService;
    private final ExportImportService exportImportService;
    private final AutoSaveManager autoSaveManager;

    private ReadmeDocument currentDocument;
    private TextArea markdownEditor;
    private WebView previewWebView;
    private Label documentTitleLabel;
    private ListView<ReadmeDocument> documentListView;

    public EditorView(Stage primaryStage, User currentUser, LoginController loginController) {
        this(primaryStage, currentUser, loginController, null);
    }

    /**
     * Creates an EditorView with an optional pre-selected document to open.
     */
    public EditorView(Stage primaryStage, User currentUser, LoginController loginController,
                      ReadmeDocument initialDocument) {
        this.primaryStage = primaryStage;
        this.currentUser = currentUser;
        this.loginController = loginController;
        this.readmeController = new ReadmeController();
        this.dashboardController = new DashboardController();
        this.versionController = new VersionController();
        this.templateController = new TemplateController();
        this.searchController = new SearchController();
        this.markdownService = new MarkdownService();
        this.exportImportService = new ExportImportService();
        this.autoSaveManager = new AutoSaveManager(
                new com.readmeeditor.service.ReadmeService());
        this.currentDocument = initialDocument;
    }

    /**
     * Builds and returns the main editor scene.
     */
    public Scene createScene() {
        BorderPane root = new BorderPane();

        // Sidebar
        VBox sidebar = createSidebar();
        root.setLeft(sidebar);

        // Toolbar
        ToolBar toolbar = createToolbar();
        root.setTop(toolbar);

        // Center: Split pane with editor and preview
        SplitPane editorSplitPane = createEditorPane();
        root.setCenter(editorSplitPane);

        // Status bar
        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 1400, 900);
        ThemeManager.getInstance().registerScene(scene);

        // Load user's documents
        refreshDocumentList();

        // Open initial document if provided
        if (currentDocument != null) {
            Platform.runLater(() -> openDocument(currentDocument));
        }

        return scene;
    }

    /**
     * Creates the sidebar with navigation buttons and document list.
     */
    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");

        // Header
        Label logo = new Label("README Editor");
        logo.getStyleClass().addAll("sidebar-header", "logo");
        VBox.setMargin(logo, new Insets(0, 0, 8, 0));

        // Nav buttons
        Label navLabel = new Label("NAVIGATION");
        navLabel.getStyleClass().add("nav-section-label");

        Button dashboardBtn = createNavButton("📊  Dashboard");
        Button newDocBtn = createNavButton("📄  New Document");
        Button openBtn = createNavButton("📂  Open");
        Button templatesBtn = createNavButton("📋  Templates");
        Button searchBtn = createNavButton("🔍  Search");
        Button versionBtn = createNavButton("🕐  Version History");
        navLabel.setPadding(new Insets(8, 16, 4, 16));

        // Document list
        Label docsLabel = new Label("DOCUMENTS");
        docsLabel.getStyleClass().add("nav-section-label");

        documentListView = new ListView<>();
        documentListView.getStyleClass().add("doc-list");
        documentListView.setPrefHeight(0); // Will grow via VBox
        VBox.setVgrow(documentListView, Priority.ALWAYS);

        documentListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ReadmeDocument doc, boolean empty) {
                super.updateItem(doc, empty);
                if (empty || doc == null) {
                    setText(null);
                } else {
                    VBox cell = new VBox(2);
                    cell.getStyleClass().add("doc-list-cell");
                    Label title = new Label(doc.getTitle());
                    title.getStyleClass().add("doc-title");
                    Label meta = new Label("v" + doc.getCurrentVersion() + " · "
                            + doc.getUpdatedAt().toLocalDate().toString());
                    meta.getStyleClass().add("doc-meta");
                    cell.getChildren().addAll(title, meta);
                    setGraphic(cell);
                }
            }
        });

        documentListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> {
                    if (selected != null) {
                        openDocument(selected);
                    }
                });

        // Event handlers
        dashboardBtn.setOnAction(e -> showDashboard());
        newDocBtn.setOnAction(e -> createNewDocument());
        openBtn.setOnAction(e -> openFileImport());
        templatesBtn.setOnAction(e -> showTemplateDialog());
        searchBtn.setOnAction(e -> showSearchDialog());
        versionBtn.setOnAction(e -> showVersionHistory());

        // Logout button at bottom
        Button logoutBtn = createNavButton("🚪  Logout");
        logoutBtn.setOnAction(e -> logout());

        sidebar.getChildren().addAll(
                logo, navLabel, dashboardBtn, newDocBtn, openBtn, templatesBtn, searchBtn, versionBtn,
                docsLabel, documentListView, logoutBtn
        );

        return sidebar;
    }

    /**
     * Creates the toolbar with action buttons.
     */
    private ToolBar createToolbar() {
        ToolBar toolbar = new ToolBar();
        toolbar.getStyleClass().add("toolbar");

        documentTitleLabel = new Label("No document open");
        documentTitleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button saveBtn = new Button("💾 Save");
        saveBtn.getStyleClass().add("toolbar-button");
        saveBtn.setOnAction(e -> saveCurrentDocument("Manual save"));

        Button previewBtn = new Button("👁 Preview");
        previewBtn.getStyleClass().add("toolbar-button");
        previewBtn.setOnAction(e -> refreshPreview());

        Button exportBtn = new Button("📥 Export");
        exportBtn.getStyleClass().add("toolbar-button");
        exportBtn.setOnAction(e -> exportDocument());

        Button themeBtn = new Button("🎨 Theme");
        themeBtn.getStyleClass().add("toolbar-button");
        themeBtn.setOnAction(e -> ThemeManager.getInstance().toggleTheme());

        toolbar.getItems().addAll(documentTitleLabel, spacer, saveBtn, previewBtn, exportBtn, themeBtn);
        return toolbar;
    }

    /**
     * Creates the split pane with Markdown editor and live preview.
     */
    private SplitPane createEditorPane() {
        SplitPane splitPane = new SplitPane();
        splitPane.getStyleClass().add("split-pane");
        splitPane.setDividerPositions(0.5);

        // Left: Editor
        VBox editorPane = new VBox();
        editorPane.getStyleClass().add("editor-pane");

        Label editorTitle = new Label("Markdown Editor");
        editorTitle.getStyleClass().add("editor-title-bar");

        markdownEditor = new TextArea();
        markdownEditor.getStyleClass().add("markdown-editor");
        markdownEditor.setWrapText(true);
        markdownEditor.setPromptText("Start typing your README content here...\n\n# Heading 1\n## Heading 2\n\n- List item\n- Another item\n\n**Bold** and *italic* text\n\n```java\ncode block\n```");

        // Live preview on text change
        markdownEditor.textProperty().addListener((obs, old, newText) -> {
            if (currentDocument != null) {
                currentDocument.setContent(newText);
                autoSaveManager.updateContent(newText);
            }
            refreshPreview();
        });

        VBox.setVgrow(markdownEditor, Priority.ALWAYS);
        editorPane.getChildren().addAll(editorTitle, markdownEditor);

        // Right: Preview
        VBox previewPane = new VBox();
        previewPane.getStyleClass().add("editor-pane");

        Label previewTitle = new Label("Live Preview");
        previewTitle.getStyleClass().add("editor-title-bar");

        previewWebView = new WebView();
        previewWebView.getStyleClass().add("markdown-preview");
        VBox.setVgrow(previewWebView, Priority.ALWAYS);

        previewPane.getChildren().addAll(previewTitle, previewWebView);

        splitPane.getItems().addAll(editorPane, previewPane);
        return splitPane;
    }

    /**
     * Creates the status bar at the bottom.
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(16);
        statusBar.setPadding(new Insets(6, 16, 6, 16));
        statusBar.setStyle("-fx-background-color: -toolbar-bg; -fx-border-color: -divider-color; -fx-border-width: 1 0 0 0;");
        statusBar.setAlignment(Pos.CENTER_LEFT);

        Label userLabel = new Label("User: " + currentUser.getUsername());
        userLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -text-muted;");

        Label docCountLabel = new Label();
        docCountLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -text-muted;");
        updateDocCountLabel(docCountLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label redisStatus = new Label("🔴 Redis");
        redisStatus.setStyle("-fx-font-size: 12px;");

        // Check Redis connection in background
        new Thread(() -> {
            boolean connected = com.readmeeditor.config.RedisConfig.getInstance().isConnected();
            Platform.runLater(() -> {
                redisStatus.setText(connected ? "🟢 Redis Connected" : "🔴 Redis Disconnected");
                redisStatus.setStyle(connected
                        ? "-fx-font-size: 12px; -fx-text-fill: #2e7d32;"
                        : "-fx-font-size: 12px; -fx-text-fill: #c62828;");
            });
        }).start();

        statusBar.getChildren().addAll(userLabel, docCountLabel, spacer, redisStatus);
        return statusBar;
    }

    /**
     * Helper to create a nav button with consistent styling.
     */
    private Button createNavButton(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    // ==================== Document Operations ====================

    /**
     * Creates a new README document.
     */
    private void createNewDocument() {
        TextInputDialog dialog = new TextInputDialog("Project README");
        dialog.setTitle("New Document");
        dialog.setHeaderText("Create a new README document");
        dialog.setContentText("Document title:");
        dialog.getDialogPane().getStyleClass().add("dialog-pane");

        dialog.showAndWait().ifPresent(title -> {
            ReadmeDocument doc = readmeController.createDocument(
                    title, "", currentUser.getId());
            openDocument(doc);
            refreshDocumentList();
            LOG.info("Created new document: {}", title);
        });
    }

    /**
     * Opens a document in the editor.
     */
    private void openDocument(ReadmeDocument doc) {
        currentDocument = doc;
        documentTitleLabel.setText(doc.getTitle());
        markdownEditor.setText(doc.getContent() != null ? doc.getContent() : "");
        autoSaveManager.setCurrentDocument(doc);
        refreshPreview();

        // Select in list
        documentListView.getSelectionModel().select(doc);
    }

    /**
     * Saves the current document.
     */
    private void saveCurrentDocument(String message) {
        if (currentDocument == null) {
            showAlert("No document open", "Open or create a document before saving.");
            return;
        }

        currentDocument.setContent(markdownEditor.getText());
        readmeController.saveDocument(currentDocument, currentUser.getId(), message);
        refreshDocumentList();
        showStatus("Document saved: " + currentDocument.getTitle());
    }

    /**
     * Exports the current document to a file.
     */
    private void exportDocument() {
        if (currentDocument == null) {
            showAlert("No document open", "Open a document before exporting.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export README");
        fileChooser.setInitialFileName(currentDocument.getTitle().replaceAll("\\s+", "_") + ".md");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Markdown Files", "*.md", "*.markdown"));

        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            try {
                currentDocument.setContent(markdownEditor.getText());
                readmeController.exportDocument(currentDocument, file.getAbsolutePath());
                showStatus("Exported to: " + file.getName());
            } catch (IOException e) {
                LOG.error("Export failed", e);
                showAlert("Export Error", "Failed to export document: " + e.getMessage());
            }
        }
    }

    /**
     * Imports a file from disk.
     */
    private void openFileImport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import README File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Markdown Files", "*.md", "*.markdown", "*.txt"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("All Files", "*.*"));

        File file = fileChooser.showOpenDialog(primaryStage);
        if (file != null) {
            try {
                ReadmeDocument doc = readmeController.importDocument(file.getAbsolutePath(), currentUser.getId());
                openDocument(doc);
                refreshDocumentList();
                showStatus("Imported: " + file.getName());
            } catch (IOException e) {
                LOG.error("Import failed", e);
                showAlert("Import Error", "Failed to import file: " + e.getMessage());
            }
        }
    }

    /**
     * Refreshes the Markdown preview.
     */
    private void refreshPreview() {
        String html = markdownService.renderToHtml(markdownEditor.getText());
        previewWebView.getEngine().loadContent(html);
    }

    /**
     * Refreshes the document list in the sidebar.
     */
    private void refreshDocumentList() {
        List<ReadmeDocument> docs = readmeController.getUserDocuments(currentUser.getId());
        documentListView.getItems().setAll(docs);
    }

    // ==================== Dialog Methods ====================

    /**
     * Shows the template selection dialog.
     */
    private void showTemplateDialog() {
        // This would be implemented as a full template dialog
        ListView<String> templateList = new ListView<>();
        templateList.getItems().addAll(templateController.getTemplateNames(currentUser.getId()));
        templateList.setPrefHeight(200);

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Select Template");
        dialog.setHeaderText("Choose a template for your new README");
        dialog.getDialogPane().setContent(templateList);
        dialog.getDialogPane().getStyleClass().add("dialog-pane");

        ButtonType selectBtn = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(selectBtn, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == selectBtn) {
                return templateList.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(templateName -> {
            if (templateName != null) {
                try {
                    ReadmeDocument doc = templateController.createFromTemplate(templateName, currentUser.getId());
                    openDocument(doc);
                    refreshDocumentList();
                    showStatus("Created from template: " + templateName);
                } catch (IllegalArgumentException e) {
                    showAlert("Template Error", e.getMessage());
                }
            }
        });
    }

    /**
     * Shows the search dialog.
     */
    private void showSearchDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Search Documents");
        dialog.setHeaderText("Search README documents by title or content");
        dialog.setContentText("Search query:");
        dialog.getDialogPane().getStyleClass().add("dialog-pane");

        dialog.showAndWait().ifPresent(query -> {
            if (query != null && !query.trim().isEmpty()) {
                var results = searchController.search(query);
                if (results.isEmpty()) {
                    showStatus("No results found for: " + query);
                } else {
                    showStatus("Found " + results.size() + " result(s) for: " + query);
                    // Open the top result
                    ReadmeDocument topResult = results.get(0).getDocument();
                    openDocument(topResult);
                }
            }
        });
    }

    /**
     * Shows the version history dialog for the current document.
     */
    private void showVersionHistory() {
        if (currentDocument == null) {
            showAlert("No document open", "Open a document to view its version history.");
            return;
        }

        var versions = versionController.getVersionHistory(currentDocument.getId());

        if (versions.isEmpty()) {
            showStatus("No version history available.");
            return;
        }

        ListView<com.readmeeditor.model.ReadmeVersion> versionList = new ListView<>();
        versionList.getItems().addAll(versions);
        versionList.setPrefHeight(300);
        versionList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(com.readmeeditor.model.ReadmeVersion v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null);
                } else {
                    VBox cell = new VBox(2);
                    cell.getStyleClass().add("version-item");
                    Label num = new Label("Version " + v.getVersionNumber());
                    num.getStyleClass().add("version-number");
                    Label date = new Label(v.getCreatedAt().toString());
                    date.getStyleClass().add("version-date");
                    Label msg = new Label(v.getCommitMessage());
                    msg.getStyleClass().add("version-message");
                    cell.getChildren().addAll(num, date, msg);
                    setGraphic(cell);
                }
            }
        });

        Dialog<com.readmeeditor.model.ReadmeVersion> dialog = new Dialog<>();
        dialog.setTitle("Version History - " + currentDocument.getTitle());
        dialog.setHeaderText("Select a version to restore or compare");
        dialog.getDialogPane().setContent(versionList);
        dialog.getDialogPane().getStyleClass().add("dialog-pane");

        ButtonType restoreBtn = new ButtonType("Restore Selected", ButtonBar.ButtonData.APPLY);
        dialog.getDialogPane().getButtonTypes().addAll(restoreBtn, ButtonType.CLOSE);

        dialog.setResultConverter(btn -> {
            if (btn == restoreBtn) {
                return versionList.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(version -> {
            if (version != null) {
                try {
                    ReadmeDocument restored = versionController.restoreVersion(
                            currentDocument.getId(), version.getVersionNumber(), currentUser.getId());
                    openDocument(restored);
                    showStatus("Restored version " + version.getVersionNumber());
                } catch (Exception e) {
                    showAlert("Restore Error", e.getMessage());
                }
            }
        });
    }

    /**
     * Shows the dashboard view.
     */
    private void showDashboard() {
        DashboardView dashboardView = new DashboardView(
                primaryStage, currentUser, loginController);
        Scene dashboardScene = dashboardView.createScene();
        primaryStage.setScene(dashboardScene);
    }

    /**
     * Logs out and returns to the login screen.
     */
    private void logout() {
        autoSaveManager.close();
        loginController.logout();

        LoginView loginView = new LoginView(primaryStage, loginController, () -> {
            EditorView editorView = new EditorView(
                    primaryStage, loginController.getCurrentUser(), loginController);
            primaryStage.setScene(editorView.createScene());
        });

        primaryStage.setScene(loginView.createScene());
    }

    /**
     * Shows an alert dialog.
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().getStyleClass().add("dialog-pane");
        alert.showAndWait();
    }

    /**
     * Shows a brief status message in the console (since JavaFX doesn't have a
     * built-in status bar easily accessible from here).
     */
    private void showStatus(String message) {
        LOG.info(message);
    }

    private void updateDocCountLabel(Label label) {
        long count = dashboardController.getUserDocumentCount(currentUser.getId());
        label.setText("Documents: " + count);
    }
}
