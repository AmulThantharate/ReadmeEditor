# Setup Guide 🚀 — README Editor

This document provides a comprehensive step-by-step guide to configure, build, run, deploy, test, and integrate the **README Editor** application.

---

## 📋 Table of Contents

1. [💻 Prerequisites](#1--prerequisites)
2. [🚀 Local Setup & Run](#2--local-setup--run)
   - [A. Run Redis Backend](#a-run-redis-backend)
   - [B. Compile & Package JAR](#b-compile--package-jar)
   - [C. Run the Application](#c-run-the-application)
3. [📁 Multi-Workspace & Notion UI Features](#3--multi-workspace--notion-ui-features)
4. [🏥 Health Checks & Smoke Tests](#4--health-checks--smoke-tests)
5. [🌐 Production Nginx Setup](#5--production-nginx-setup)
6. [🐸 JFrog Artifactory Configuration](#6--jfrog-artifactory-configuration)
7. [⚙️ GitHub Actions CI/CD](#7--github-actions-cicd)
8. [📊 Jenkins Pipeline Configuration](#8--jenkins-pipeline-configuration)

---

## 1. 💻 Prerequisites

Ensure the following tools are installed on your host machine:

*   **Java Development Kit (JDK 21 or later):** Adoptium Temurin JDK 21 is recommended.
*   **Apache Maven 3.9+:** For compiling and packaging dependencies.
*   **Docker Desktop:** For containerized Redis and deployment testing.

### Verify Installation:
```bash
java --version
mvn --version
docker --version
```

---

## 2. 🚀 Local Setup & Run

### A. Run Redis Backend
The application relies on Redis 7 for user data, workspaces, document persistence, and presence tracking.

**Option A: Using Docker (Recommended)**
Start the containerized Redis stack defined in `docker-compose.yml`:
```bash
docker-compose up -d redis
```

**Option B: Local Redis Server**
If running Redis natively on your host machine, ensure it listens on port `6379`:
```bash
redis-server --port 6379
```

### B. Compile & Package JAR
Compile the project source files, run unit tests, and build a shaded "fat" JAR containing all dependencies:
```bash
mvn clean package
```
*Note: Tests will run automatically. If Docker is not active on your host machine, integration tests utilizing Testcontainers will gracefully skip themselves.*

### C. Run the Application

**Option A: Headless Server Mode (Recommended for Web Client access)**
Starts the application purely as a web server on port `8080` (no JavaFX UI initialized):
```bash
java -jar target/readme-editor-1.0.0.jar --server
```
Open your browser and navigate to: **[http://localhost:8080](http://localhost:8080)**

**Option B: Desktop GUI + Web Server concurrent mode**
Starts the JavaFX desktop client and runs the web server in the background:
```bash
java -jar target/readme-editor-1.0.0.jar
```

---

## 3. 📁 Multi-Workspace & Notion UI Features

The application supports isolated Notion-style multi-workspaces:

*   **Workspaces Selector:** Located at the top of the sidebar. You can switch between active workspaces, create a new workspace (via `+ Create Workspace`), or delete a workspace (which wipes its nested documents).
*   **Automatic Migration:** Legacy files created without a workspace are dynamically migrated into a default `Personal Workspace` upon a user's first login.
*   **Mermaid.js Rendering:** Code blocks formatted as ` ```mermaid ` inside documents render automatically as SVG diagrams in the preview iframe, adjusting dynamically to Light/Dark modes.
*   **Image Drag-and-Drop & Clipboard Paste:** Drag-and-drop an image or copy-paste it directly from your clipboard into the editor. The asset is automatically uploaded to the server's uploads volume and the Markdown link is inserted at the cursor position.

---

## 4. 🏥 Health Checks & Smoke Tests

### Health Endpoint
Verify the operational health of the application by making a `GET` request:
*   **URL:** `http://localhost:8080/api/health`
*   **Format:** JSON
*   **Response Structure:**
    ```json
    {
      "status": "UP",
      "redis": "UP",
      "timestamp": "2026-06-15T14:02:11.123",
      "jvmFreeMemoryBytes": 100219280,
      "jvmMaxMemoryBytes": 2147483648,
      "jvmTotalMemoryBytes": 134217728
    }
    ```

### Run Smoke Tests
Smoke test scripts are provided to verify that the health check endpoint returns `status: UP` and static web assets are correctly served.

**On Windows (PowerShell):**
```powershell
powershell -ExecutionPolicy Bypass -File .\smoke-test.ps1
```

**On Linux / macOS (Bash):**
```bash
chmod +x smoke-test.sh
./smoke-test.sh
```

---

## 5. 🌐 Production Nginx Setup

To serve static files at high performance and route API calls to the Java backend process, configure Nginx as a reverse proxy using the provided `nginx.conf`:

1.  **Extract static files:** Ensure the frontend files (`index.html`, `app.js`, `style.css`) are located in a folder like `/var/www/readme-editor`.
2.  **Copy Configuration:** Move `nginx.conf` to `/etc/nginx/sites-available/readme-editor`.
3.  **Activate & Reload:**
    ```bash
    sudo ln -s /etc/nginx/sites-available/readme-editor /etc/nginx/sites-enabled/
    sudo systemctl reload nginx
    ```

---

## 6. 🐸 JFrog Artifactory Configuration

1.  **Log in** to your JFrog Platform console.
2.  **Create Repositories:** Navigate to **Administration > Repositories > Local** and create two Maven repositories:
    *   `maven-release-local` (for release builds)
    *   `maven-snapshot-local` (for snapshot builds)
3.  **Generate Access Token:** Go to **Application > User Profile > Access Tokens** and generate a token with write access to Maven repositories.

---

## 7. ⚙️ GitHub Actions CI/CD

Configure GitHub Actions to compile and deploy the shaded package on push to the `main` branch.

1.  In your GitHub repository, navigate to **Settings > Secrets and variables > Actions**.
2.  Add the following repository secrets:
    *   🔑 `JF_URL` — Your JFrog Platform URL (e.g., `https://mycompany.jfrog.io`).
    *   🔑 `JF_ACCESS_TOKEN` — The Access Token generated from JFrog in Step 6.
3.  The workflow configuration is fully automated under [.github/workflows/jfrog-publish.yml](file:///.github/workflows/jfrog-publish.yml).

---

## 8. 📊 Jenkins Pipeline Configuration

Deploy builds dynamically to JFrog using the declarative [Jenkinsfile](file:///Jenkinsfile).

1.  **Install Plugin:** Go to **Manage Jenkins > Plugins** and install the **Artifactory** plugin.
2.  **Configure Server Connection:**
    *   Go to **Manage Jenkins > System**.
    *   Add a server in the **Artifactory** section:
        *   **Server ID:** `jfrog-artifactory-server`
        *   **URL:** `https://mycompany.jfrog.io/artifactory`
        *   **Credentials:** Configure your JFrog username and access token credentials.
3.  **Set Up Tooling:** Go to **Manage Jenkins > Tools**:
    *   Add a JDK installation named `jdk-21` pointing to Java 21.
    *   Add a Maven installation named `maven-3.9` pointing to Maven 3.9+.
4.  **Create Pipeline:** Create a Jenkins Pipeline job, set definition to **Pipeline script from SCM**, link your repository URL, and specify **`Jenkinsfile`** as the script path.
