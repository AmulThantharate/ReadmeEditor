package com.readmeeditor;

import com.readmeeditor.config.AppConfig;
import com.readmeeditor.config.RedisConfig;
import com.readmeeditor.controller.LoginController;
import com.readmeeditor.util.ThemeManager;
import com.readmeeditor.view.EditorView;
import com.readmeeditor.view.LoginView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the README Editor application.
 * <p>
 * A JavaFX desktop application for creating, editing, previewing, and managing
 * README.md files with Redis-based persistence.
 * <p>
 * <h2>Startup Sequence</h2>
 * <ol>
 *   <li>Load application configuration from {@code application.properties}</li>
 *   <li>Verify Redis connection</li>
 *   <li>Display login screen</li>
 *   <li>On successful login, enter the main editor</li>
 * </ol>
 *
 * @see <a href="https://codebuff.com">README Editor</a>
 */
public class ReadmeEditorApplication extends Application {
    private static final Logger LOG = LoggerFactory.getLogger(ReadmeEditorApplication.class);

    private RedisConfig redisConfig;
    private static com.readmeeditor.web.WebServer webServer;

    public static void main(String[] args) {
        boolean headless = java.awt.GraphicsEnvironment.isHeadless();
        boolean forceServer = false;
        for (String arg : args) {
            if ("--server".equals(arg)) {
                forceServer = true;
                break;
            }
        }

        if (headless || forceServer) {
            LOG.info("Starting README Editor in headless Web Server mode...");
            AppConfig.getInstance();
            if (RedisConfig.getInstance().isConnected()) {
                LOG.info("Redis connection established successfully.");
            } else {
                LOG.warn("Redis connection failed. Ensure Redis is running.");
            }
            webServer = new com.readmeeditor.web.WebServer();
            webServer.start();
            
            // Keep main thread alive for the web server in headless mode
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                LOG.error("Server execution interrupted", e);
            }
        } else {
            // Start Web Server in background for GUI mode
            webServer = new com.readmeeditor.web.WebServer();
            webServer.start();
            launch(args);
        }
    }

    @Override
    public void init() {
        LOG.info("========================================");
        LOG.info("  README Editor v{}", AppConfig.getInstance().getAppVersion());
        LOG.info("========================================");

        // Initialize configuration and Redis
        LOG.info("Starting README Editor...");
        AppConfig.getInstance();
        redisConfig = RedisConfig.getInstance();

        // Verify Redis connection
        LOG.info("Verifying Redis connection to {}:{}...",
                AppConfig.getInstance().getRedisHost(),
                AppConfig.getInstance().getRedisPort());

        if (redisConfig.isConnected()) {
            LOG.info("Redis connection established successfully.");
        } else {
            LOG.warn("Redis connection failed. The application will start but database operations may fail. " +
                    "Ensure Redis is running via 'docker-compose up -d'.");
        }
    }

    @Override
    public void start(Stage primaryStage) {
        LOG.info("Initializing JavaFX UI...");

        // Configure the primary stage
        primaryStage.setTitle(AppConfig.getInstance().getAppName() + " v" + AppConfig.getInstance().getAppVersion());
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);

        // Set the default theme
        ThemeManager.getInstance().setTheme(AppConfig.getInstance().getDefaultTheme());

        // Create the login controller (shared across views)
        LoginController loginController = new LoginController();

        // Create login view with a callback on successful login
        LoginView loginView = new LoginView(primaryStage, loginController, () -> {
            // On login success, open the editor view
            EditorView editorView = new EditorView(
                    primaryStage, loginController.getCurrentUser(), loginController);
            primaryStage.setScene(editorView.createScene());
        });

        // Show login screen
        primaryStage.setScene(loginView.createScene());
        primaryStage.show();

        LOG.info("Application UI initialized successfully.");
    }

    @Override
    public void stop() {
        LOG.info("Shutting down README Editor...");

        // Stop Web Server
        if (webServer != null) {
            webServer.stop();
            LOG.info("Web Server stopped.");
        }

        // Close Redis connections
        if (redisConfig != null) {
            redisConfig.close();
            LOG.info("Redis connection pool closed.");
        }

        LOG.info("Application shutdown complete.");
    }
}
