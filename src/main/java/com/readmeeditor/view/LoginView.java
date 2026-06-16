package com.readmeeditor.view;

import com.readmeeditor.controller.LoginController;
import com.readmeeditor.util.ThemeManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Login view providing authentication and registration forms.
 * <p>
 * Displays a centered card with username/password fields and
 * toggles between login and registration modes.
 */
public class LoginView {
    private static final Logger LOG = LoggerFactory.getLogger(LoginView.class);

    private final Stage primaryStage;
    private final LoginController loginController;
    private final Runnable onLoginSuccess;
    private boolean registrationMode = false;

    public LoginView(Stage primaryStage, LoginController loginController, Runnable onLoginSuccess) {
        this.primaryStage = primaryStage;
        this.loginController = loginController;
        this.onLoginSuccess = onLoginSuccess;
    }

    /**
     * Builds and returns the login scene.
     */
    public Scene createScene() {
        VBox root = new VBox();
        root.getStyleClass().add("login-pane");

        // Login card
        VBox card = new VBox();
        card.getStyleClass().add("login-card");
        card.setMaxWidth(420);
        card.setAlignment(Pos.TOP_CENTER);

        // Title
        Label title = new Label("README Editor");
        title.getStyleClass().add("title");

        Label subtitle = new Label();
        subtitle.getStyleClass().add("subtitle");

        // Form fields
        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.getStyleClass().add("text-field-custom");
        VBox.setMargin(usernameField, new Insets(0, 0, 12, 0));

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.getStyleClass().add("text-field-custom");
        VBox.setMargin(passwordField, new Insets(0, 0, 12, 0));

        TextField emailField = new TextField();
        emailField.setPromptText("Email (optional)");
        emailField.getStyleClass().add("text-field-custom");
        emailField.setVisible(false);
        emailField.setManaged(false);
        VBox.setMargin(emailField, new Insets(0, 0, 12, 0));

        // Error label
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #e03131; -fx-font-size: 13px;");
        errorLabel.setVisible(false);
        VBox.setMargin(errorLabel, new Insets(0, 0, 8, 0));

        // Submit button
        Button submitButton = new Button("Log In");
        submitButton.getStyleClass().addAll("dialog-button", "primary");
        submitButton.setMaxWidth(Double.MAX_VALUE);
        submitButton.setPrefHeight(40);
        VBox.setMargin(submitButton, new Insets(8, 0, 12, 0));

        // Toggle link
        Hyperlink toggleLink = new Hyperlink("Don't have an account? Register");
        toggleLink.setAlignment(Pos.CENTER);

        // Theme toggle
        Button themeToggle = new Button("Toggle Theme");
        themeToggle.getStyleClass().add("dialog-button");
        themeToggle.setStyle("-fx-font-size: 12px; -fx-padding: 4px 12px;");
        HBox themeBox = new HBox(themeToggle);
        themeBox.setAlignment(Pos.CENTER_RIGHT);

        // Assemble card
        card.getChildren().addAll(themeBox, title, subtitle, usernameField, passwordField,
                emailField, errorLabel, submitButton, toggleLink);

        root.getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);

        StackPane stackPane = new StackPane(root);
        stackPane.setAlignment(Pos.CENTER);

        // Event handlers
        toggleLink.setOnAction(e -> {
            registrationMode = !registrationMode;
            if (registrationMode) {
                subtitle.setText("Create your account to get started");
                submitButton.setText("Create Account");
                toggleLink.setText("Already have an account? Log in");
                emailField.setVisible(true);
                emailField.setManaged(true);
            } else {
                subtitle.setText("Sign in to manage your README files");
                submitButton.setText("Log In");
                toggleLink.setText("Don't have an account? Register");
                emailField.setVisible(false);
                emailField.setManaged(false);
            }
            errorLabel.setVisible(false);
        });

        submitButton.setOnAction(e -> handleSubmit(
                usernameField.getText(),
                passwordField.getText(),
                emailField.getText(),
                errorLabel
        ));

        // Enter key triggers submit
        passwordField.setOnAction(e -> handleSubmit(
                usernameField.getText(),
                passwordField.getText(),
                emailField.getText(),
                errorLabel
        ));
        usernameField.setOnAction(e -> passwordField.requestFocus());

        // Theme toggle
        themeToggle.setOnAction(e -> {
            ThemeManager.getInstance().toggleTheme();
        });

        Scene scene = new Scene(stackPane, 420, 520);
        ThemeManager.getInstance().registerScene(scene);

        return scene;
    }

    /**
     * Handles form submission for both login and registration.
     */
    private void handleSubmit(String username, String password, String email, Label errorLabel) {
        errorLabel.setVisible(false);

        if (username == null || username.trim().isEmpty()) {
            errorLabel.setText("Please enter your username");
            errorLabel.setVisible(true);
            return;
        }
        if (password == null || password.isEmpty()) {
            errorLabel.setText("Please enter your password");
            errorLabel.setVisible(true);
            return;
        }

        try {
            if (registrationMode) {
                loginController.register(username.trim(), password, email != null ? email.trim() : "");
                LOG.info("User registered: {}", username);
            } else {
                loginController.login(username.trim(), password);
                LOG.info("User logged in: {}", username);
            }
            onLoginSuccess.run();
        } catch (IllegalArgumentException e) {
            errorLabel.setText(e.getMessage());
            errorLabel.setVisible(true);
        } catch (Exception e) {
            LOG.error("Authentication error", e);
            errorLabel.setText("Connection error. Is Redis running?");
            errorLabel.setVisible(true);
        }
    }
}
