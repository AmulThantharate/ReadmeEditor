package com.readmeeditor.view;

import com.readmeeditor.controller.DashboardController;
import com.readmeeditor.controller.LoginController;
import com.readmeeditor.controller.ReadmeController;
import com.readmeeditor.model.ReadmeDocument;
import com.readmeeditor.model.User;
import com.readmeeditor.util.ThemeManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Dashboard view displaying aggregate statistics, recent documents,
 * most-edited documents, and storage usage information.
 */
public class DashboardView {
    private static final Logger LOG = LoggerFactory.getLogger(DashboardView.class);

    private final Stage primaryStage;
    private final User currentUser;
    private final LoginController loginController;
    private final DashboardController dashboardController;
    private final ReadmeController readmeController;

    public DashboardView(Stage primaryStage, User currentUser, LoginController loginController) {
        this.primaryStage = primaryStage;
        this.currentUser = currentUser;
        this.loginController = loginController;
        this.dashboardController = new DashboardController();
        this.readmeController = new ReadmeController();
    }

    /**
     * Builds and returns the dashboard scene.
     */
    public Scene createScene() {
        BorderPane root = new BorderPane();

        // Top bar
        HBox topBar = createTopBar();
        root.setTop(topBar);

        // Main content area with scroll
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        VBox content = new VBox(24);
        content.setPadding(new Insets(32));

        // Welcome section
        Label welcomeLabel = new Label("Welcome, " + currentUser.getUsername() + " 👋");
        welcomeLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: -text-primary;");

        Label subLabel = new Label("Here's an overview of your README documents");
        subLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: -text-secondary;");
        subLabel.setPadding(new Insets(0, 0, 8, 0));

        VBox welcomeSection = new VBox(4, welcomeLabel, subLabel);

        // Stats cards grid
        GridPane statsGrid = createStatsGrid();
        statsGrid.setHgap(16);
        statsGrid.setVgap(16);

        // Recent documents section
        VBox recentSection = createRecentDocumentsSection();

        // Most edited section
        VBox mostEditedSection = createMostEditedSection();

        // Storage stats
        VBox storageSection = createStorageSection();

        content.getChildren().addAll(welcomeSection, statsGrid, recentSection, mostEditedSection, storageSection);
        scrollPane.setContent(content);
        root.setCenter(scrollPane);

        Scene scene = new Scene(root, 1200, 800);
        ThemeManager.getInstance().registerScene(scene);

        return scene;
    }

    /**
     * Creates the top bar with navigation and theme toggle.
     */
    private HBox createTopBar() {
        HBox topBar = new HBox(12);
        topBar.setPadding(new Insets(12, 24, 12, 24));
        topBar.setStyle("-fx-background-color: -toolbar-bg; -fx-border-color: -divider-color; -fx-border-width: 0 0 1 0;");
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Dashboard");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: -text-primary;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button editorBtn = new Button("📝 Open Editor");
        editorBtn.getStyleClass().add("toolbar-button");
        editorBtn.setOnAction(e -> navigateToEditor());

        Button themeBtn = new Button("🎨 Toggle Theme");
        themeBtn.getStyleClass().add("toolbar-button");
        themeBtn.setOnAction(e -> ThemeManager.getInstance().toggleTheme());

        Button logoutBtn = new Button("🚪 Logout");
        logoutBtn.getStyleClass().add("toolbar-button");
        logoutBtn.setOnAction(e -> logout());

        Region spacer2 = new Region();
        spacer2.setPrefWidth(16);

        topBar.getChildren().addAll(title, spacer, editorBtn, themeBtn, logoutBtn);
        return topBar;
    }

    /**
     * Creates the statistics grid with cards.
     */
    private GridPane createStatsGrid() {
        GridPane grid = new GridPane();

        long totalFiles = dashboardController.getTotalReadmeFiles();
        long totalUsers = dashboardController.getTotalUsers();
        long totalVersions = dashboardController.getTotalVersions();
        long userDocs = dashboardController.getUserDocumentCount(currentUser.getId());

        grid.add(createStatCard("📄", "Total README Files", String.valueOf(totalFiles)), 0, 0);
        grid.add(createStatCard("👥", "Total Users", String.valueOf(totalUsers)), 1, 0);
        grid.add(createStatCard("🔄", "Total Versions", String.valueOf(totalVersions)), 2, 0);
        grid.add(createStatCard("📁", "My Documents", String.valueOf(userDocs)), 3, 0);

        // Make columns equal width
        for (int i = 0; i < 4; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(25);
            grid.getColumnConstraints().add(col);
        }

        return grid;
    }

    /**
     * Creates a single stat card.
     */
    private VBox createStatCard(String icon, String label, String value) {
        VBox card = new VBox(8);
        card.getStyleClass().add("stat-card");
        card.setPrefHeight(120);

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 28px;");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stat-value");

        Label descLabel = new Label(label);
        descLabel.getStyleClass().add("stat-label");

        card.getChildren().addAll(iconLabel, valueLabel, descLabel);
        return card;
    }

    /**
     * Creates the recent documents section.
     */
    private VBox createRecentDocumentsSection() {
        VBox section = new VBox(12);
        section.setPadding(new Insets(8, 0, 8, 0));

        Label header = new Label("📂 Recent Documents");
        header.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: -text-primary;");

        List<ReadmeDocument> recentDocs = dashboardController.getUserRecentDocuments(currentUser.getId(), 5);

        if (recentDocs.isEmpty()) {
            Label empty = new Label("No documents yet. Create your first README!");
            empty.setStyle("-fx-text-fill: -text-muted; -fx-font-size: 14px;");
            section.getChildren().addAll(header, empty);
        } else {
            VBox docList = new VBox(8);
            for (ReadmeDocument doc : recentDocs) {
                HBox docItem = createDocumentItem(doc);
                docList.getChildren().add(docItem);
            }
            section.getChildren().addAll(header, docList);
        }

        return section;
    }

    /**
     * Creates the most edited documents section.
     */
    private VBox createMostEditedSection() {
        VBox section = new VBox(12);
        section.setPadding(new Insets(8, 0, 8, 0));

        Label header = new Label("🔥 Most Edited");
        header.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: -text-primary;");

        List<ReadmeDocument> mostEdited = dashboardController.getMostEditedDocuments(5);

        if (mostEdited.isEmpty()) {
            Label empty = new Label("No documents available.");
            empty.setStyle("-fx-text-fill: -text-muted; -fx-font-size: 14px;");
            section.getChildren().addAll(header, empty);
        } else {
            VBox docList = new VBox(8);
            for (ReadmeDocument doc : mostEdited) {
                HBox docItem = createDocumentItem(doc);
                docList.getChildren().add(docItem);
            }
            section.getChildren().addAll(header, docList);
        }

        return section;
    }

    /**
     * Creates a single document item row.
     */
    private HBox createDocumentItem(ReadmeDocument doc) {
        HBox item = new HBox(16);
        item.setPadding(new Insets(12, 16, 12, 16));
        item.setStyle("-fx-background-color: -card-bg; -fx-border-color: -divider-color; " +
                "-fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px; " +
                "-fx-cursor: hand;");
        item.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(2);
        Label title = new Label(doc.getTitle());
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: 600; -fx-text-fill: -text-primary;");
        Label meta = new Label("v" + doc.getCurrentVersion() + " · Last updated: "
                + doc.getUpdatedAt().toLocalDate() + " · " + doc.getUpdatedAt().toLocalTime().toString().substring(0, 5));
        meta.setStyle("-fx-font-size: 12px; -fx-text-fill: -text-muted;");
        info.getChildren().addAll(title, meta);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button openBtn = new Button("Open");
        openBtn.setStyle("-fx-background-color: -accent-color; -fx-text-fill: white; " +
                "-fx-padding: 6px 16px; -fx-border-radius: 4px; -fx-background-radius: 4px; " +
                "-fx-cursor: hand; -fx-font-size: 12px;");

        openBtn.setOnAction(e -> navigateToEditorWithDocument(doc));

        item.getChildren().addAll(info, spacer, openBtn);

        // Click on item also navigates
        item.setOnMouseClicked(e -> navigateToEditorWithDocument(doc));

        return item;
    }

    /**
     * Creates the storage statistics section.
     */
    private VBox createStorageSection() {
        VBox section = new VBox(12);
        section.setPadding(new Insets(8, 0, 8, 0));

        Label header = new Label("💾 Storage Statistics");
        header.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: -text-primary;");

        Map<String, Object> stats = dashboardController.getStorageStatistics(currentUser.getId());

        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(24);
        statsGrid.setVgap(8);
        statsGrid.setPadding(new Insets(8, 0, 8, 0));

        int row = 0;
        for (Map.Entry<String, Object> entry : stats.entrySet()) {
            Label keyLabel = new Label(formatKey(entry.getKey()) + ":");
            keyLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: -text-secondary; -fx-font-weight: 600;");
            Label valueLabel = new Label(String.valueOf(entry.getValue()));
            valueLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: -text-primary;");
            statsGrid.add(keyLabel, 0, row);
            statsGrid.add(valueLabel, 1, row);
            row++;
        }

        section.getChildren().addAll(header, statsGrid);
        return section;
    }

    /**
     * Formats a camelCase key to a readable label.
     */
    private String formatKey(String key) {
        return key.replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .replace("KB", " (KB)")
                .replace("Size", " Size");
    }

    /**
     * Navigates back to the editor.
     */
    private void navigateToEditor() {
        EditorView editorView = new EditorView(primaryStage, currentUser, loginController);
        primaryStage.setScene(editorView.createScene());
    }

    /**
     * Navigates to the editor with a specific document open.
     */
    private void navigateToEditorWithDocument(ReadmeDocument doc) {
        EditorView editorView = new EditorView(primaryStage, currentUser, loginController, doc);
        primaryStage.setScene(editorView.createScene());
    }

    /**
     * Logs out.
     */
    private void logout() {
        LoginView loginView = new LoginView(primaryStage, loginController, () -> {
            EditorView editorView = new EditorView(primaryStage, loginController.getCurrentUser(), loginController);
            primaryStage.setScene(editorView.createScene());
        });
        primaryStage.setScene(loginView.createScene());
    }
}
