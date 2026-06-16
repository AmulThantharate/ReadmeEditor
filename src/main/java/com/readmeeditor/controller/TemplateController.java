package com.readmeeditor.controller;

import com.readmeeditor.model.ReadmeDocument;
import com.readmeeditor.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * Controller for template-related operations from the UI.
 * <p>
 * Provides built-in templates, allows creating documents from templates,
 * and supports user-defined custom templates persisted to Redis.
 */
public class TemplateController {
    private static final Logger LOG = LoggerFactory.getLogger(TemplateController.class);

    private final TemplateService templateService;

    public TemplateController() {
        this.templateService = new TemplateService();
    }

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    /**
     * Returns all templates available to a user (built-in + custom).
     *
     * @param userId the user ID for custom template lookup
     * @return map of template name to template content
     */
    public Map<String, String> getAllTemplates(String userId) {
        return templateService.getAllTemplates(userId);
    }

    /**
     * Returns only the built-in templates.
     */
    public Map<String, String> getBuiltInTemplates() {
        return templateService.getBuiltInTemplates();
    }

    /**
     * Gets the content of a specific template by name (checks built-in then custom).
     *
     * @param templateName the template name
     * @param userId       the user ID for custom template lookup
     * @return the template content, or null if not found
     */
    public String getTemplateContent(String templateName, String userId) {
        return templateService.getTemplate(templateName, userId);
    }

    /**
     * Creates a new README document from a template.
     *
     * @param templateName the template name to use
     * @param userId       the creating user's ID
     * @return the created document
     */
    public ReadmeDocument createFromTemplate(String templateName, String userId) {
        LOG.info("Creating document from template '{}' for user {}", templateName, userId);
        return templateService.createFromTemplate(templateName, userId);
    }

    /**
     * Returns template names as an array (includes custom templates).
     *
     * @param userId the user ID for custom template lookup
     */
    public String[] getTemplateNames(String userId) {
        return getAllTemplates(userId).keySet().toArray(new String[0]);
    }

    /**
     * Saves a custom template for a user.
     *
     * @param userId   the user ID
     * @param name     the template name
     * @param content  the Markdown template content
     */
    public void saveCustomTemplate(String userId, String name, String content) {
        LOG.info("Saving custom template '{}' for user {}", name, userId);
        templateService.saveCustomTemplate(userId, name, content);
    }

    /**
     * Returns all custom templates for a user.
     *
     * @param userId the user ID
     * @return map of template name to content
     */
    public Map<String, String> getCustomTemplates(String userId) {
        return templateService.getCustomTemplates(userId);
    }

    /**
     * Returns custom template names for a user.
     *
     * @param userId the user ID
     * @return set of template names
     */
    public Set<String> getCustomTemplateNames(String userId) {
        return templateService.getCustomTemplateNames(userId);
    }

    /**
     * Deletes a custom template.
     *
     * @param userId the user ID
     * @param name   the template name to delete
     */
    public void deleteCustomTemplate(String userId, String name) {
        LOG.info("Deleting custom template '{}' for user {}", name, userId);
        templateService.deleteCustomTemplate(userId, name);
    }

    /**
     * Checks if a template name exists.
     *
     * @param name   the template name
     * @param userId the user ID for custom template check
     */
    public boolean templateExists(String name, String userId) {
        return templateService.templateExists(name, userId);
    }
}
