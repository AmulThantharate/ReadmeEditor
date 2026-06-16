// =============================================================================
// GLOBAL STATE & API CLIENT
// =============================================================================

const API_BASE = window.location.origin;

let currentUser = null;
let workspaces = [];
let currentWorkspace = null;
let documents = [];
let currentDoc = null;
let saveTimeout = null;
let previewTimeout = null;
let currentLayout = 'split'; // split, edit, preview
let compareVersion1 = null;
let presenceInterval = null;

// =============================================================================
// PAGE LOAD & INITIALIZATION
// =============================================================================

document.addEventListener("DOMContentLoaded", () => {
    // Load cached session
    const cachedUser = localStorage.getItem("readme_editor_user");
    if (cachedUser) {
        currentUser = JSON.parse(cachedUser);
        showWorkspace();
    } else {
        showLogin();
    }

    // Event Listeners Setup
    setupEventHandlers();
});

function showLogin() {
    document.getElementById("login-overlay").classList.remove("hidden");
    document.getElementById("app-container").classList.add("hidden");
}

async function showWorkspace() {
    document.getElementById("login-overlay").classList.add("hidden");
    document.getElementById("app-container").classList.remove("hidden");
    
    // Fetch workspaces & documents
    await fetchWorkspaces();
}

// =============================================================================
// API FETCH HELPERS
// =============================================================================

async function apiCall(endpoint, method = "GET", body = null) {
    const headers = {
        "Content-Type": "application/json"
    };
    if (currentUser) {
        headers["X-User-Id"] = currentUser.id;
    }

    const config = {
        method,
        headers
    };

    if (body) {
        config.body = JSON.stringify(body);
    }

    const res = await fetch(`${API_BASE}${endpoint}`, config);
    if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || "Something went wrong");
    }

    if (res.status === 204) return null;
    return await res.json();
}

// =============================================================================
// AUTHENTICATION
// =============================================================================

async function handleAuthSubmit(e) {
    e.preventDefault();
    const username = document.getElementById("username").value;
    const password = document.getElementById("password").value;
    const email = document.getElementById("email").value;
    const errorDiv = document.getElementById("auth-error");
    const isRegister = !document.querySelector(".register-only").classList.contains("hidden");

    errorDiv.classList.add("hidden");

    try {
        let user;
        if (isRegister) {
            user = await apiCall("/api/auth/register", "POST", { username, password, email });
        } else {
            user = await apiCall("/api/auth/login", "POST", { username, password });
        }

        currentUser = user;
        localStorage.setItem("readme_editor_user", JSON.stringify(user));
        showWorkspace();
    } catch (err) {
        errorDiv.textContent = err.message;
        errorDiv.classList.remove("hidden");
    }
}

// =============================================================================
// WORKSPACE OPERATIONS
// =============================================================================

async function fetchWorkspaces() {
    try {
        workspaces = await apiCall("/api/workspaces");
        if (workspaces.length > 0) {
            const savedWsId = localStorage.getItem("readme_editor_current_workspace_id");
            const match = workspaces.find(w => w.id === savedWsId);
            currentWorkspace = match || workspaces[0];
            localStorage.setItem("readme_editor_current_workspace_id", currentWorkspace.id);
        } else {
            currentWorkspace = null;
            localStorage.removeItem("readme_editor_current_workspace_id");
        }
        renderWorkspaceSelector();
        await fetchDocuments();
    } catch (err) {
        console.error("Failed to load workspaces", err);
    }
}

function renderWorkspaceSelector() {
    const display = document.getElementById("workspace-name-display");
    if (currentWorkspace) {
        display.textContent = currentWorkspace.name;
    } else {
        display.textContent = "No Workspace";
    }

    const list = document.getElementById("workspace-dropdown-list");
    list.innerHTML = "";

    workspaces.forEach(ws => {
        const item = document.createElement("div");
        item.className = `workspace-item ${currentWorkspace && currentWorkspace.id === ws.id ? 'active' : ''}`;
        
        item.innerHTML = `
            <span class="workspace-icon">🏢</span>
            <span class="workspace-item-name">${ws.name}</span>
            ${workspaces.length > 1 ? '<span class="btn-delete-workspace" title="Delete Workspace">✕</span>' : ''}
        `;

        item.addEventListener("click", (e) => {
            if (e.target.classList.contains("btn-delete-workspace")) {
                e.stopPropagation();
                deleteWorkspace(ws.id);
            } else {
                selectWorkspace(ws);
            }
        });

        list.appendChild(item);
    });
}

async function selectWorkspace(ws) {
    currentWorkspace = ws;
    localStorage.setItem("readme_editor_current_workspace_id", ws.id);
    document.getElementById("workspace-dropdown").classList.add("hidden");
    renderWorkspaceSelector();
    
    // Reload documents for this workspace
    await fetchDocuments();
}

async function createWorkspace() {
    const name = prompt("Enter new workspace name:", "My Workspace");
    if (!name) return;
    const clean = name.trim();
    if (!clean) return;

    try {
        const ws = await apiCall("/api/workspaces", "POST", { name: clean });
        workspaces.push(ws);
        await selectWorkspace(ws);
    } catch (err) {
        alert("Failed to create workspace: " + err.message);
    }
}

async function deleteWorkspace(id) {
    if (!confirm("Are you sure you want to delete this workspace? All its pages will be permanently deleted!")) return;
    try {
        await apiCall(`/api/workspaces/${id}`, "DELETE");
        workspaces = workspaces.filter(w => w.id !== id);
        
        if (currentWorkspace && currentWorkspace.id === id) {
            currentWorkspace = workspaces[0] || null;
            if (currentWorkspace) {
                localStorage.setItem("readme_editor_current_workspace_id", currentWorkspace.id);
            } else {
                localStorage.removeItem("readme_editor_current_workspace_id");
            }
        }
        renderWorkspaceSelector();
        await fetchDocuments();
    } catch (err) {
        alert("Failed to delete workspace: " + err.message);
    }
}

// =============================================================================
// =============================================================================
// COLLABORATION PRESENCE OPERATIONS
// =============================================================================

async function pollPresence(docId) {
    if (!currentUser || !currentDoc) return;
    try {
        const activeUsers = await apiCall(`/api/documents/${docId}/presence`, "POST");
        renderActiveUsers(activeUsers);
    } catch (err) {
        console.error("Presence sync failed", err);
    }
}

function renderActiveUsers(users) {
    const container = document.getElementById("presence-users");
    if (!container) return;
    container.innerHTML = "";

    users.forEach((u) => {
        const avatar = document.createElement("div");
        const colorIdx = (u.username.charCodeAt(0) + u.username.length) % 6;
        avatar.className = `presence-avatar color-${colorIdx}`;
        
        const initials = u.username.substring(0, 2);
        avatar.textContent = initials;
        avatar.title = u.username === currentUser.username ? `${u.username} (You)` : u.username;

        container.appendChild(avatar);
    });
}

// =============================================================================
// ASSET UPLOAD OPERATIONS
// =============================================================================

async function handleAssetUpload(file) {
    if (!file.type.startsWith("image/")) {
        alert("Only image uploads are supported!");
        return;
    }

    const indicator = document.getElementById("autosave-status");
    indicator.textContent = "Uploading image...";

    const reader = new FileReader();
    reader.onload = async function(event) {
        const base64Data = event.target.result;
        try {
            const res = await apiCall("/api/assets", "POST", {
                filename: file.name || "clipboard.png",
                base64: base64Data
            });

            const textarea = document.getElementById("markdown-input");
            const startPos = textarea.selectionStart;
            const endPos = textarea.selectionEnd;
            const text = textarea.value;
            const markdownLink = `\n![${file.name || 'image'}](${res.url})\n`;

            textarea.value = text.substring(0, startPos) + markdownLink + text.substring(endPos);
            textarea.selectionStart = textarea.selectionEnd = startPos + markdownLink.length;

            indicator.textContent = "✓ Image uploaded";
            triggerPreviewUpdate();
            triggerAutoSave();
        } catch (err) {
            alert("Upload failed: " + err.message);
            indicator.textContent = "Upload failed";
        }
    };
    reader.readAsDataURL(file);
}

// =============================================================================
// DOCUMENTS OPERATIONS
// =============================================================================

async function fetchDocuments() {
    if (!currentWorkspace) {
        documents = [];
        renderDocsList();
        
        // Clear editor fields since no documents are loaded
        document.getElementById("doc-emoji").textContent = "📝";
        document.getElementById("doc-title-input").value = "";
        document.getElementById("breadcrumb-doc-title").textContent = "Untitled";
        document.getElementById("markdown-input").value = "";
        const iframe = document.getElementById("preview-output");
        const doc = iframe.contentDocument || iframe.contentWindow.document;
        doc.open();
        doc.write("");
        doc.close();
        return;
    }
    try {
        documents = await apiCall(`/api/documents?workspaceId=${currentWorkspace.id}`);
        renderDocsList();
        
        if (documents.length > 0) {
            selectDocument(documents[0].id);
        } else {
            createNewDocument();
        }
    } catch (err) {
        console.error("Failed to load documents", err);
    }
}

function renderDocsList() {
    const container = document.getElementById("docs-list");
    container.innerHTML = "";

    documents.forEach(doc => {
        const item = document.createElement("div");
        item.className = `doc-item ${currentDoc && currentDoc.id === doc.id ? 'active' : ''}`;
        item.dataset.id = doc.id;

        // Parse emoji out of title if possible, or use default
        let emoji = "📄";
        let title = doc.title || "Untitled";

        if (title.startsWith(":") || /^\p{Emoji}/u.test(title)) {
            // Title has leading emoji
            const match = title.match(/^(\p{Emoji_Presentation}|\p{Emoji})\s*(.*)/u);
            if (match) {
                emoji = match[1];
                title = match[2];
            }
        }

        item.innerHTML = `
            <span class="doc-emoji">${emoji}</span>
            <span class="doc-title">${title}</span>
            <span class="btn-delete-doc" title="Delete Page">✕</span>
        `;

        item.addEventListener("click", (e) => {
            if (e.target.classList.contains("btn-delete-doc")) {
                e.stopPropagation();
                deleteDocument(doc.id);
            } else {
                selectDocument(doc.id);
            }
        });

        container.appendChild(item);
    });
}

async function selectDocument(id) {
    try {
        currentDoc = await apiCall(`/api/documents/${id}`);
        
        // Update document title input
        let rawTitle = currentDoc.title || "Untitled";
        let emoji = "📝";
        
        const emojiMatch = rawTitle.match(/^(\p{Emoji_Presentation}|\p{Emoji})\s*(.*)/u);
        if (emojiMatch) {
            emoji = emojiMatch[1];
            rawTitle = emojiMatch[2];
        }

        document.getElementById("doc-emoji").textContent = emoji;
        document.getElementById("doc-title-input").value = rawTitle;
        document.getElementById("breadcrumb-doc-title").textContent = rawTitle;
        
        // Update body content
        document.getElementById("markdown-input").value = currentDoc.content || "";
        
        // Update tags
        renderTags();

        // Refresh preview
        renderPreview();

        // Highlight active document in sidebar
        renderDocsList();

        // Setup user presence tracking for collaboration
        if (presenceInterval) clearInterval(presenceInterval);
        pollPresence(id);
        presenceInterval = setInterval(() => pollPresence(id), 3000);
    } catch (err) {
        console.error("Failed to select document", err);
    }
}

async function createNewDocument(title = "Untitled", content = "") {
    if (!currentWorkspace) {
        alert("Please select or create a workspace first.");
        return;
    }
    try {
        const doc = await apiCall("/api/documents", "POST", { 
            title: `📝 ${title}`, 
            content,
            workspaceId: currentWorkspace.id 
        });
        currentDoc = doc;
        documents.unshift(doc);
        renderDocsList();
        selectDocument(doc.id);
    } catch (err) {
        console.error("Failed to create document", err);
    }
}

async function deleteDocument(id) {
    if (!confirm("Are you sure you want to delete this page?")) return;
    try {
        await apiCall(`/api/documents/${id}`, "DELETE");
        documents = documents.filter(d => d.id !== id);
        renderDocsList();
        
        if (currentDoc && currentDoc.id === id) {
            if (documents.length > 0) {
                selectDocument(documents[0].id);
            } else {
                createNewDocument();
            }
        }
    } catch (err) {
        console.error("Failed to delete document", err);
    }
}

// =============================================================================
// AUTO-SAVE & PREVIEW DEBOUNCE
// =============================================================================

function triggerAutoSave() {
    const indicator = document.getElementById("autosave-status");
    indicator.textContent = "Saving...";
    
    if (saveTimeout) clearTimeout(saveTimeout);
    
    saveTimeout = setTimeout(async () => {
        if (!currentDoc) return;

        const emoji = document.getElementById("doc-emoji").textContent;
        const rawTitle = document.getElementById("doc-title-input").value || "Untitled";
        const title = `${emoji} ${rawTitle}`;
        const content = document.getElementById("markdown-input").value;
        const tagIds = currentDoc.tagIds || [];

        try {
            const updated = await apiCall(`/api/documents/${currentDoc.id}`, "PUT", {
                title,
                content,
                tagIds,
                createVersion: false
            });
            
            // Sync with sidebar list
            const index = documents.findIndex(d => d.id === currentDoc.id);
            if (index !== -1) {
                documents[index].title = title;
                documents[index].content = content;
                renderDocsList();
            }
            
            document.getElementById("breadcrumb-doc-title").textContent = rawTitle;
            indicator.textContent = "✓ Saved";
        } catch (err) {
            console.error("Auto-save failed", err);
            indicator.textContent = "Error saving";
        }
    }, 1500);
}

function triggerPreviewUpdate() {
    if (previewTimeout) clearTimeout(previewTimeout);
    previewTimeout = setTimeout(renderPreview, 300);
}

async function renderPreview() {
    const markdown = document.getElementById("markdown-input").value;
    const iframe = document.getElementById("preview-output");
    
    try {
        const res = await apiCall("/api/markdown", "POST", { markdown });
        const doc = iframe.contentDocument || iframe.contentWindow.document;
        doc.open();
        doc.write(res.html);
        doc.close();

        // Inject dark mode styles if dark theme is active
        const isDark = document.body.classList.contains("dark-theme");
        if (isDark) {
            const style = doc.createElement("style");
            style.textContent = `
                body {
                    background-color: #191919 !important;
                    color: rgba(255, 255, 255, 0.9) !important;
                }
                h1, h2 {
                    border-bottom-color: rgba(255, 255, 255, 0.15) !important;
                }
                code {
                    background-color: rgba(255, 255, 255, 0.1) !important;
                }
                pre {
                    background-color: #202020 !important;
                    border-color: rgba(255, 255, 255, 0.1) !important;
                }
                blockquote {
                    border-left-color: rgba(255, 255, 255, 0.2) !important;
                    color: rgba(255, 255, 255, 0.6) !important;
                }
                th, td {
                    border-color: rgba(255, 255, 255, 0.1) !important;
                }
                th {
                    background-color: #202020 !important;
                }
                tr:nth-child(even) {
                    background-color: rgba(255, 255, 255, 0.02) !important;
                }
            `;
            doc.head.appendChild(style);
        }
    } catch (err) {
        console.error("Preview failed", err);
    }
}

// =============================================================================
// MANUAL VERSION SAVE
// =============================================================================

async function handleManualSave() {
    if (!currentDoc) return;
    const msg = prompt("Enter a description for this version / commit message:", "Updated README documentation");
    if (msg === null) return; // cancelled

    const indicator = document.getElementById("autosave-status");
    indicator.textContent = "Saving Version...";

    const emoji = document.getElementById("doc-emoji").textContent;
    const rawTitle = document.getElementById("doc-title-input").value || "Untitled";
    const title = `${emoji} ${rawTitle}`;
    const content = document.getElementById("markdown-input").value;
    const tagIds = currentDoc.tagIds || [];

    try {
        const updated = await apiCall(`/api/documents/${currentDoc.id}`, "PUT", {
            title,
            content,
            tagIds,
            createVersion: true,
            commitMessage: msg
        });
        
        // Sync layout
        currentDoc = updated;
        indicator.textContent = "✓ Version Saved";
        alert("Version saved successfully!");
    } catch (err) {
        alert("Failed to save version: " + err.message);
        indicator.textContent = "Error saving";
    }
}

// =============================================================================
// TAGS MANAGEMENT
// =============================================================================

function renderTags() {
    const container = document.getElementById("doc-tags");
    container.innerHTML = "";
    
    const tags = currentDoc.tagIds || [];
    tags.forEach(tagId => {
        const badge = document.createElement("span");
        badge.className = "tag-badge";
        badge.innerHTML = `
            <span>${tagId}</span>
            <span class="btn-remove-tag" data-tag="${tagId}">✕</span>
        `;
        badge.querySelector(".btn-remove-tag").addEventListener("click", () => removeTag(tagId));
        container.appendChild(badge);
    });
}

async function addTag() {
    if (!currentDoc) return;
    const name = prompt("Enter a new tag name:");
    if (!name) return;
    
    const cleanName = name.trim().toLowerCase();
    if (!cleanName) return;

    if (!currentDoc.tagIds) currentDoc.tagIds = [];
    if (currentDoc.tagIds.includes(cleanName)) return;

    currentDoc.tagIds.push(cleanName);
    renderTags();
    triggerAutoSave();
}

function removeTag(tagId) {
    if (!currentDoc) return;
    currentDoc.tagIds = currentDoc.tagIds.filter(t => t !== tagId);
    renderTags();
    triggerAutoSave();
}

// =============================================================================
// EXPORTING AS FILE
// =============================================================================

function exportDocument() {
    if (!currentDoc) return;
    
    const rawTitle = document.getElementById("doc-title-input").value || "Untitled";
    const content = document.getElementById("markdown-input").value;
    
    const blob = new Blob([content], { type: "text/markdown;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `${rawTitle.toLowerCase().replace(/\s+/g, "-")}.md`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
}

// =============================================================================
// SEARCH MODAL
// =============================================================================

async function triggerSearch() {
    const query = document.getElementById("search-input").value;
    const resultsContainer = document.getElementById("search-results");
    resultsContainer.innerHTML = "<div class='modal-desc'>Searching...</div>";

    try {
        const results = await apiCall(`/api/search?q=${encodeURIComponent(query)}`);
        resultsContainer.innerHTML = "";
        
        if (results.length === 0) {
            resultsContainer.innerHTML = "<div class='modal-desc'>No pages found</div>";
            return;
        }

        results.forEach(doc => {
            const item = document.createElement("div");
            item.className = "search-item";
            
            let emoji = "📄";
            let title = doc.title || "Untitled";

            const match = title.match(/^(\p{Emoji_Presentation}|\p{Emoji})\s*(.*)/u);
            if (match) {
                emoji = match[1];
                title = match[2];
            }

            item.innerHTML = `
                <span class="item-emoji">${emoji}</span>
                <div class="item-info">
                    <div class="item-title">${title}</div>
                    <div class="item-snippet">${doc.content ? doc.content.substring(0, 100) + '...' : 'No content'}</div>
                </div>
            `;

            item.addEventListener("click", () => {
                selectDocument(doc.id);
                toggleModal("search-modal", false);
            });

            resultsContainer.appendChild(item);
        });
    } catch (err) {
        resultsContainer.innerHTML = `<div class='auth-error'>Search failed: ${err.message}</div>`;
    }
}

// =============================================================================
// TEMPLATES MODAL
// =============================================================================

async function loadTemplates() {
    const grid = document.getElementById("templates-grid");
    grid.innerHTML = "<div class='modal-desc'>Loading templates...</div>";

    try {
        const names = await apiCall("/api/templates");
        grid.innerHTML = "";
        
        names.forEach(name => {
            const card = document.createElement("div");
            card.className = "template-card";
            
            let emoji = "📋";
            if (name.includes("Java")) emoji = "☕";
            if (name.includes("Spring")) emoji = "🍃";
            if (name.includes("API")) emoji = "📡";
            if (name.includes("Library")) emoji = "📦";

            card.innerHTML = `
                <div class="card-icon">${emoji}</div>
                <h4>${name}</h4>
                <p>Standard ${name} README layout.</p>
            `;

            card.addEventListener("click", () => applyTemplate(name));
            grid.appendChild(card);
        });
    } catch (err) {
        grid.innerHTML = `<div class='auth-error'>Failed to load templates: ${err.message}</div>`;
    }
}

async function applyTemplate(name) {
    if (!currentDoc) return;
    if (!confirm(`Apply the template '${name}'? This will append the template details to your current page.`)) return;

    try {
        const res = await apiCall(`/api/templates/${encodeURIComponent(name)}`);
        
        const input = document.getElementById("markdown-input");
        const currentContent = input.value;
        const separator = currentContent ? "\n\n---\n\n" : "";
        input.value = currentContent + separator + res.content;
        
        triggerPreviewUpdate();
        triggerAutoSave();
        toggleModal("templates-modal", false);
    } catch (err) {
        alert("Failed to load template content: " + err.message);
    }
}

// =============================================================================
// VERSION HISTORY & COMPARING (DIFF)
// =============================================================================

async function loadVersions() {
    if (!currentDoc) return;
    const container = document.getElementById("versions-list");
    container.innerHTML = "<div class='modal-desc'>Loading version history...</div>";

    try {
        const versions = await apiCall(`/api/documents/${currentDoc.id}/versions`);
        container.innerHTML = "";
        compareVersion1 = null;

        if (versions.length === 0) {
            container.innerHTML = "<div class='modal-desc'>No saved versions found. Save a version manually to start tracking.</div>";
            return;
        }

        versions.forEach(v => {
            const item = document.createElement("div");
            item.className = "version-item";
            
            const date = new Date(v.timestamp).toLocaleString();

            item.innerHTML = `
                <div class="version-meta">
                    <span class="version-num">v${v.versionNumber}</span>
                    <span>${date}</span>
                </div>
                <div class="version-msg">${v.commitMessage || "No commit message"}</div>
                <div class="version-actions">
                    <button class="btn btn-secondary btn-sm btn-restore-ver">Restore</button>
                    <button class="btn btn-secondary btn-sm btn-compare-ver">Compare</button>
                </div>
            `;

            // Restore action
            item.querySelector(".btn-restore-ver").addEventListener("click", () => restoreVersion(v.versionNumber));
            
            // Compare action
            item.querySelector(".btn-compare-ver").addEventListener("click", () => {
                if (compareVersion1 === null) {
                    compareVersion1 = v.versionNumber;
                    alert(`Selected v${v.versionNumber} for comparison. Now click 'Compare' on another version.`);
                } else {
                    compareVersions(compareVersion1, v.versionNumber);
                }
            });

            container.appendChild(item);
        });
    } catch (err) {
        container.innerHTML = `<div class='auth-error'>Failed to load versions: ${err.message}</div>`;
    }
}

async function restoreVersion(versionNum) {
    if (!confirm(`Restore this document to version ${versionNum}?`)) return;
    try {
        const doc = await apiCall(`/api/documents/${currentDoc.id}/versions/${versionNum}/restore`, "POST");
        currentDoc = doc;
        toggleModal("versions-drawer", false);
        selectDocument(doc.id);
        alert(`Document restored to version ${versionNum}!`);
    } catch (err) {
        alert("Failed to restore version: " + err.message);
    }
}

async function compareVersions(v1, v2) {
    try {
        const res = await apiCall(`/api/documents/${currentDoc.id}/compare?v1=${v1}&v2=${v2}`);
        const output = document.getElementById("diff-output");
        output.innerHTML = "";
        
        const lines = res.diff.split("\n");
        lines.forEach(line => {
            const div = document.createElement("div");
            div.className = "diff-line";
            
            if (line.startsWith("+")) {
                div.classList.add("added");
            } else if (line.startsWith("-")) {
                div.classList.add("removed");
            } else if (line.startsWith("@@")) {
                div.classList.add("header");
            }
            
            div.textContent = line;
            output.appendChild(div);
        });
        
        toggleModal("diff-modal", true);
    } catch (err) {
        alert("Failed to run diff: " + err.message);
    }
}

// =============================================================================
// UI TRIGGERS & LISTENERS
// =============================================================================

function setupEventHandlers() {
    // Workspace switcher dropdown toggle
    document.getElementById("current-workspace-display").addEventListener("click", (e) => {
        e.stopPropagation();
        document.getElementById("workspace-dropdown").classList.toggle("hidden");
    });

    // Close dropdown when clicking outside
    document.addEventListener("click", () => {
        const dropdown = document.getElementById("workspace-dropdown");
        if (dropdown) dropdown.classList.add("hidden");
    });

    // Create workspace button
    document.getElementById("btn-add-workspace").addEventListener("click", (e) => {
        e.stopPropagation();
        document.getElementById("workspace-dropdown").classList.add("hidden");
        createWorkspace();
    });

    // Auth Toggle
    document.getElementById("toggle-auth-btn").addEventListener("click", (e) => {
        e.preventDefault();
        const registerFields = document.querySelector(".register-only");
        const submitBtn = document.getElementById("submit-btn");
        const text = document.getElementById("toggle-text");
        const toggleBtn = document.getElementById("toggle-auth-btn");

        if (registerFields.classList.contains("hidden")) {
            registerFields.classList.remove("hidden");
            submitBtn.textContent = "Sign Up";
            text.textContent = "Already have an account?";
            toggleBtn.textContent = "Sign In";
        } else {
            registerFields.classList.add("hidden");
            submitBtn.textContent = "Sign In";
            text.textContent = "Don't have an account?";
            toggleBtn.textContent = "Sign Up";
        }
    });

    // Auth Submit
    document.getElementById("login-form").addEventListener("submit", handleAuthSubmit);

    // Logout
    document.getElementById("btn-logout").addEventListener("click", () => {
        currentUser = null;
        localStorage.removeItem("readme_editor_user");
        if (presenceInterval) clearInterval(presenceInterval);
        showLogin();
    });

    // Theme Toggle
    document.getElementById("btn-theme-toggle").addEventListener("click", () => {
        const body = document.body;
        const icon = document.getElementById("theme-icon");
        const text = document.getElementById("theme-text");

        if (body.classList.contains("light-theme")) {
            body.className = "dark-theme";
            icon.textContent = "☀️";
            text.textContent = "Light Mode";
        } else {
            body.className = "light-theme";
            icon.textContent = "🌙";
            text.textContent = "Dark Mode";
        }
        
        // Update preview theme instantly
        renderPreview();
    });

    // Document Title & Content edit listeners
    document.getElementById("doc-title-input").addEventListener("input", () => {
        triggerAutoSave();
    });
    
    // Emoji click trigger
    document.getElementById("doc-emoji").addEventListener("click", () => {
        const emojis = ["📝", "📄", "🚀", "☕", "🍃", "⚙️", "🧪", "🐳", "📂", "🔍", "📋", "🕐", "📥", "🎨", "🚪", "👤", "👀", "🛠️", "🏗️", "📦", "🔐", "🤝", "📄"];
        const current = document.getElementById("doc-emoji").textContent;
        const nextIdx = (emojis.indexOf(current) + 1) % emojis.length;
        document.getElementById("doc-emoji").textContent = emojis[nextIdx === -1 ? 0 : nextIdx];
        triggerAutoSave();
    });

    const markdownInput = document.getElementById("markdown-input");
    markdownInput.addEventListener("input", () => {
        triggerPreviewUpdate();
        triggerAutoSave();
    });

    // Drag-over hover style
    markdownInput.addEventListener("dragover", (e) => {
        e.preventDefault();
        markdownInput.classList.add("drag-over");
    });

    markdownInput.addEventListener("dragleave", () => {
        markdownInput.classList.remove("drag-over");
    });

    // Drag-and-drop image file upload
    markdownInput.addEventListener("drop", (e) => {
        e.preventDefault();
        markdownInput.classList.remove("drag-over");
        const files = e.dataTransfer.files;
        if (files.length > 0) {
            handleAssetUpload(files[0]);
        }
    });

    // Clipboard copy-paste image upload
    markdownInput.addEventListener("paste", (e) => {
        const items = (e.clipboardData || e.originalEvent.clipboardData).items;
        for (let item of items) {
            if (item.kind === 'file') {
                e.preventDefault();
                const blob = item.getAsFile();
                handleAssetUpload(blob);
            }
        }
    });

    // Sidebar Action Buttons
    document.getElementById("btn-new-doc").addEventListener("click", () => createNewDocument());
    document.getElementById("btn-save").addEventListener("click", handleManualSave);
    document.getElementById("btn-export").addEventListener("click", exportDocument);
    document.getElementById("btn-add-tag").addEventListener("click", addTag);

    // Sidebar responsive toggle drawer
    const container = document.getElementById("app-container");
    document.getElementById("btn-sidebar-toggle").addEventListener("click", (e) => {
        e.stopPropagation();
        container.classList.toggle("sidebar-open");
    });
    
    // Close sidebar drawer if clicked outside of it on mobile
    document.addEventListener("click", (e) => {
        if (container.classList.contains("sidebar-open")) {
            const sidebar = document.querySelector(".sidebar");
            const toggleBtn = document.getElementById("btn-sidebar-toggle");
            if (!sidebar.contains(e.target) && !toggleBtn.contains(e.target)) {
                container.classList.remove("sidebar-open");
            }
        }
    });

    // Layout buttons
    const layoutLayout = document.getElementById("workspace-layout");
    
    document.getElementById("btn-layout-split").addEventListener("click", () => {
        layoutLayout.className = "workspace-layout split-mode";
        setActiveLayoutButton("btn-layout-split");
    });
    document.getElementById("btn-layout-edit").addEventListener("click", () => {
        layoutLayout.className = "workspace-layout edit-mode";
        setActiveLayoutButton("btn-layout-edit");
    });
    document.getElementById("btn-layout-preview").addEventListener("click", () => {
        layoutLayout.className = "workspace-layout preview-mode";
        setActiveLayoutButton("btn-layout-preview");
    });

    // Search Trigger Modal
    document.getElementById("btn-search").addEventListener("click", () => {
        toggleModal("search-modal", true);
        document.getElementById("search-input").value = "";
        triggerSearch();
        document.getElementById("search-input").focus();
    });
    document.getElementById("btn-close-search").addEventListener("click", () => toggleModal("search-modal", false));
    document.getElementById("search-input").addEventListener("input", () => {
        triggerSearch();
    });

    // Templates Trigger Modal
    document.getElementById("btn-templates").addEventListener("click", () => {
        toggleModal("templates-modal", true);
        loadTemplates();
    });
    document.getElementById("btn-close-templates").addEventListener("click", () => toggleModal("templates-modal", false));

    // Versions Drawer Trigger
    document.getElementById("btn-versions").addEventListener("click", () => {
        toggleModal("versions-drawer", true);
        loadVersions();
    });
    document.getElementById("btn-close-versions").addEventListener("click", () => toggleModal("versions-drawer", false));
    document.getElementById("btn-close-diff").addEventListener("click", () => toggleModal("diff-modal", false));

    // Global Key Shortcuts (Ctrl + K to Search)
    document.addEventListener("keydown", (e) => {
        if ((e.ctrlKey || e.metaKey) && e.key === "k") {
            e.preventDefault();
            document.getElementById("btn-search").click();
        }
    });
}

function setActiveLayoutButton(id) {
    const ids = ["btn-layout-split", "btn-layout-edit", "btn-layout-preview"];
    ids.forEach(btnId => {
        const btn = document.getElementById(btnId);
        if (btnId === id) {
            btn.classList.add("active");
        } else {
            btn.classList.remove("active");
        }
    });
}

function toggleModal(id, show) {
    const el = document.getElementById(id);
    if (show) {
        el.classList.remove("hidden");
    } else {
        el.classList.add("hidden");
    }
}
