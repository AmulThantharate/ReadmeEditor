# README Editor 📝

A **production-ready Java desktop application** for creating, editing, previewing, saving, versioning, and managing `README.md` files. Built with Java 21, JavaFX, and Redis.

---

## 📋 Table of Contents

- [✨ Features](#-features)
- [🛠️ Technology Stack](#-technology-stack)
- [🏗️ Architecture](#-architecture)
- [💻 Prerequisites](#-prerequisites)
- [🚀 Quick Start](#-quick-start)
  - [1. Start Redis](#1-start-redis)
  - [2. Build the Application](#2-build-the-application)
  - [3. Run the Application](#3-run-the-application)
- [💡 Usage Guide](#-usage-guide)
  - [Login & Registration](#login--registration)
  - [Dashboard Overview](#dashboard-overview)
  - [The Editor Workspace](#the-editor-workspace)
  - [Using Templates](#using-templates)
  - [Version Control & Diffs](#version-control--diffs)
  - [Full-Text Search](#full-text-search)
- [🗂️ Project Structure](#%EF%B8%8F-project-structure)
- [🗄️ Redis Data Model](#%EF%B8%8F-redis-data-model)
- [⚙️ Configuration](#%EF%B8%8F-configuration)
- [🧪 Testing](#-testing)
- [🐳 Docker Support](#-docker-support)
- [🐸 CI/CD & JFrog Integration](#-cicd--jfrog-integration)
  - [GitHub Actions Integration](#github-actions-integration)
  - [Jenkins Pipeline Integration](#jenkins-pipeline-integration)
- [🌐 Nginx Production Deployment](#-nginx-production-deployment)
- [🔧 Troubleshooting](#-troubleshooting)
- [🤝 Contributing](#-contributing)
- [📄 License](#-license)

---

## ✨ Features

| Feature | Description |
|:---|:---|
| 📝 **Rich Editor** | Split-screen Markdown editor with live HTML preview via WebView. |
| 💾 **Auto-Save** | Automatic draft saving to Redis at configurable intervals. |
| 📂 **Import / Export** | Import existing `.md` files; export markdown documents to any location on disk. |
| 📋 **Templates** | Pre-built templates (Project README, Java Project, Spring Boot, Library, API). |
| 🕐 **Version Control** | Save versions with commit messages, view history, restore, and compare changes with line-by-line diffs. |
| 🔍 **Search** | Full-text search across documents, titles, content, and tags with relevance ranking. |
| 👥 **User Management** | Login, registration, password hashing (BCrypt), and role-based access. |
| 📊 **Dashboard** | Stats cards, recent documents, most-edited metrics, and storage analytics. |
| 🌓 **Theme Manager** | Seamless toggle between Dark and Light mode (preference stored per user). |
| 🗄️ **Redis Backend** | In-memory performance with reliable persistence via Jedis pooling. |
| 📱 **Responsive UI** | Mobile-responsive Notion layout with collapsible sliding sidebar drawer. |
| 📁 **Workspaces** | Create multiple isolated workspaces to group documents, switch seamlessly, and organize pages. |
| 🎇 **FAANG Animations** | Glassmorphic login card with glowing animated background blobs. |

---

## 🛠️ Technology Stack

*   **Language:** Java 21 ☕
*   **Desktop UI:** JavaFX 21 (Controls, FXML, WebView) 🎨
*   **Database:** Redis 7 (via Jedis client) 🗄️
*   **Build Tool:** Apache Maven 3.9+ 🏗️
*   **Testing:** JUnit 5 + Mockito 5 + Testcontainers 🧪
*   **Logging:** SLF4J + Logback 📄
*   **Markdown Parsing:** CommonMark (supporting GFM Tables, Heading Anchors) 📝
*   **JSON Serialization:** Jackson (with JSR310 date support) 📦
*   **Security:** jBCrypt password hashing 🔐

---

## 🏗️ Architecture

The application follows a clean **MVC (Model-View-Controller)** architecture:

```
com.readmeeditor
├── config/        # Application & Redis configuration
├── controller/    # UI-facing controllers
├── service/       # Business logic layer
├── repository/    # Redis data access layer
├── model/         # Domain models (POJOs)
├── view/          # JavaFX UI components
└── util/          # Utilities (password, markdown, theme, auto-save)
```

### 🔄 Data Flow

```
User Input ──> JavaFX View ──> Controller ──> Service ──> Repository ──> Redis
                                                                    (via Jedis Pool)
```

---

## 💻 Prerequisites

- **JDK 21** or later ([Download](https://adoptium.net/)) ☕
- **Apache Maven 3.9+** ([Download](https://maven.apache.org/download.cgi)) 🏗️
- **Docker Desktop** (highly recommended for running local Redis) ([Download](https://www.docker.com/products/docker-desktop/)) 🐳
  *OR* a running Redis 7 instance on `localhost:6379` 🗄️

### Verify Installation

```bash
# Verify Java version
java --version

# Verify Maven version
mvn --version

# Verify Docker
docker --version
```

---

## 🚀 Quick Start

### 1. Start Redis

**Option A: Using Docker (Recommended)**
```bash
# Start Redis in the background
docker-compose up -d

# Verify Redis is running
docker ps

# Test connection
redis-cli ping
# Expected response: PONG
```

**Option B: Local Redis**
```bash
redis-server --port 6379
```

### 2. Build the Application

Build the project (compile, run tests, and shade jar):
```bash
mvn clean package
```

The packaged shaded JAR will be created at: `target/readme-editor-1.0.0.jar`

### 3. Run the Application

**Option A: Desktop GUI Mode + Web Server (Concurrent)**
Starts the JavaFX Desktop app and launches the Web Server in the background:
```bash
mvn javafx:run
# Or
java -jar target/readme-editor-1.0.0.jar
```
You can use the desktop application AND access the web interface at `http://localhost:8080` at the same time.

**Option B: Web Server Only (Headless mode — perfect for remote containers, Gitpod, Codespaces, VMs)**
Runs the application exclusively as a web server on port `8080` without attempting to load AWT/JavaFX graphics:
```bash
mvn javafx:run -Djavafx.run.args="--server"
# Or
java -jar target/readme-editor-1.0.0.jar --server
```
Once started, open your browser and access the Notion-like interface at:
👉 **[http://localhost:8080](http://localhost:8080)**

---

## 💡 Usage Guide

### Login & Registration
1. Click **"Don't have an account? Register"** if you are a new user.
2. Enter a username, password (minimum 8 characters), and email.
3. Click **"Create Account"** to register and login automatically.
4. On subsequent launches, simply log in using your credentials.

### Dashboard Overview
After logging in, you'll land on the dashboard containing:
- **Statistics cards**: Global and user-level stats.
- **Recent documents**: Quick-links to your latest work.
- **Most-edited metrics**: Highlights documents with the most versions.
- **Storage analytics**: Visual metrics on draft size.

### The Editor Workspace
Click **"Open Editor"** to access the workspace:
*   **Toolbar Actions:**
    *   💾 **Save** — Save changes and create a version.
    *   👁 **Preview** — Refresh the Markdown preview.
    *   📥 **Export** — Export the document to `.md` on disk.
    *   🌓 **Theme** — Toggle Light/Dark mode.
*   **Sidebar Actions:**
    *   📊 **Dashboard** — Return to the home dashboard.
    *   📄 **New Document** — Create a blank README document.
    *   📂 **Open File** — Import an existing `.md` file.
    *   📋 **Templates** — Select from pre-made README layouts.
    *   🔍 **Search** — Open full-text document search.
    *   🕐 **Version History** — Compare or roll back changes.

### Using Templates
Select **"Templates"** to start with layout skeletons optimized for:
*   `Project README` — General open-source projects
*   `Java Project` — Standard Java desktop apps/CLI tools
*   `Spring Boot` — Spring Boot web apps & microservices
*   `Library` — Reusable libraries and SDKs
*   `API Documentation` — RESTful API services

### Version Control & Diffs
Every manual save prompts you for a commit message and increments the version:
1. Click **"Version History"** in the sidebar.
2. Select a version to view details.
3. Click **"Restore"** to revert to that version.
4. Select two versions and click **"Compare"** to see line-by-line diffs:
    *   `-` lines removed in red.
    *   `+` lines added in green.

### Full-Text Search
The search pane features:
- Title and body keyword matching.
- Tag filters (e.g. `java`, `release`).
- Date range selectors.
- Relevance ranking (hits in titles rank higher than hits in content).

---

## 🗂️ Project Structure

```
D:\ReadmeEditor
├── pom.xml                               # Maven build configuration
├── docker-compose.yml                    # Redis Docker service definition
├── LICENSE                               # MIT License
├── README.md                             # This file
├── Jenkinsfile                           # Jenkins Declarative Pipeline template
├── .github/
│   └── workflows/
│       └── jfrog-publish.yml             # GitHub Actions workflow template
│
├── src/main/java/com/readmeeditor/
│   ├── ReadmeEditorApplication.java      # Application entrypoint
│   ├── module-info.java                  # Java module system configuration
│   ├── config/                           # Database & config loading
│   ├── model/                            # Data transfer objects & models
│   ├── repository/                       # Redis persistence handlers
│   ├── service/                          # Business logic layer
│   ├── controller/                       # UI Controllers
│   ├── view/                             # UI Views and panels
│   └── util/                             # Password, theme, and parser utilities
│
└── src/test/java/com/readmeeditor/
    ├── service/                          # Service layer unit tests
    └── util/                             # Utility tests
```

---

## 🗄️ Redis Data Model

### Users
- `user:{id}` (Hash) — fields: `username`, `passwordHash`, `email`, `role`, `createdAt`, `lastLoginAt`.
- `user:username:{username}` (String) — Maps username to user ID.
- `user:all` (Set) — All registered user IDs.

### README Documents
- `readme:{id}` (Hash) — fields: `title`, `content`, `createdByUserId`, `workspaceId`, `createdAt`, `updatedAt`, `currentVersion`, `tagIds`.
- `readme:{id}:versions` (Sorted Set) — JSON version history, scored by version number.
- `readme:all` (Set) — All document IDs.

### Workspaces
- `workspace:{id}` (Hash) — fields: `name`, `createdByUserId`, `createdAt`.
- `user:{userId}:workspaces` (Set) — All workspace IDs belonging to the user.

### Tags
- `tags:{id}` (String) — Tag name.
- `tags:name:{name}` (String) — Maps tag name (lowercase) to tag ID.
- `tags:all` (Set) — All tag IDs.
- `readme:tag:{tagId}` (Set) — Document IDs associated with the tag.

---

## ⚙️ Configuration

Application settings are located at [src/main/resources/application.properties](file:///D:/ReadmeEditor/src/main/resources/application.properties):

```properties
# Redis Connection
redis.host=localhost
redis.port=6379
redis.password=
redis.timeout=2000
redis.database=0

# Auto-Save
autosave.enabled=true
autosave.interval.ms=5000

# Editor Defaults
editor.default.theme=light
editor.default.font.size=14
editor.tab.size=2

# Security
security.password.min.length=8
security.bcrypt.rounds=12
```

---

## 🧪 Testing

The test suite contains unit and integration tests. Run them using Maven:

```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=VersionServiceTest
```

> ⚙️ **Note for Java 21+ / Java 25:** Tests are pre-configured to use Mockito 5's **subclass mock maker** (via [org.mockito.plugins.MockMaker](file:///D:/ReadmeEditor/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker)). This avoids JDK 21+ dynamic agent self-attachment restrictions.
>
> 🐳 **Docker-free environments:** Integration tests automatically skip themselves if Docker is not running on the local host (via `@Testcontainers(disabledWithoutDocker = true)`), preventing environment-related build failures.

---

## 🐳 Docker Support

Manage the backend Redis container:

```bash
# Start Redis container
docker-compose up -d

# Check status
docker-compose ps

# View logs
docker-compose logs -f redis

# Stop and remove containers
docker-compose down

# Stop and wipe volume data
docker-compose down -v
```

---

## 🏥 Health Check & Smoke Testing

To ensure the application is running healthily in production or CI/CD pipelines, a dedicated health check endpoint and test scripts are provided.

### Health Check Endpoint
* **URL:** `http://localhost:8080/api/health`
* **Method:** `GET`
* **Response format:** JSON
* **Example response:**
  ```json
  {
    "status": "UP",
    "redis": "UP",
    "timestamp": "2026-06-15T13:42:51.123456",
    "jvmFreeMemoryBytes": 100219280,
    "jvmMaxMemoryBytes": 2147483648,
    "jvmTotalMemoryBytes": 134217728
  }
  ```

### Running Smoke Tests
Smoke test scripts check if the health check endpoint returns `status: UP` and verify that static web assets are correctly reachable.

**On Windows (PowerShell):**
```powershell
powershell -ExecutionPolicy Bypass -File .\smoke-test.ps1
```

**On Linux / macOS / Docker (Bash):**
```bash
chmod +x smoke-test.sh
./smoke-test.sh
```

---

## 🐸 CI/CD & JFrog Integration

Integrating README Editor builds into a CI/CD pipeline and publishing build artifacts to a binary manager like **JFrog Artifactory** can be done using the provided configurations.

### GitHub Actions Integration

A complete GitHub Actions workflow is available at [.github/workflows/jfrog-publish.yml](file:///D:/ReadmeEditor/.github/workflows/jfrog-publish.yml).

#### Configuration Steps:
1. In your GitHub repository settings, go to **Settings > Secrets and variables > Actions**.
2. Add the following repository secrets:
   *   `JF_URL`: Your JFrog Platform URL (e.g. `https://myorg.jfrog.io`).
   *   `JF_ACCESS_TOKEN`: An access token generated from your JFrog Platform console with write permissions to Maven repositories.
3. Configure your local JFrog Artifactory with Maven repository targets (by default, the workflow uses `maven-release-local` and `maven-snapshot-local`).
4. On every push/PR to `main`, the workflow will:
   * Check out code and set up JDK 21.
   * Configure JFrog CLI using `jfrog/setup-jfrog-cli`.
   * Configure Maven settings dynamically.
   * Run the build, deploy the shaded `.jar` to JFrog, and publish build info.

---

### Jenkins Pipeline Integration

A Declarative Pipeline script is provided at [Jenkinsfile](file:///D:/ReadmeEditor/Jenkinsfile).

#### Configuration Steps:
1. Install the **Jenkins Artifactory Plugin** via the Jenkins plugin manager.
2. In **Manage Jenkins > Configure System**, define an Artifactory Server under the *Artifactory* section with:
   *   **Server ID:** `jfrog-artifactory-server` (must match the ID in the Jenkinsfile)
   *   **URL:** Your JFrog Artifactory URL
   *   **Credentials:** Setup your JFrog username & password/token.
3. In Jenkins, create a new **Pipeline** job pointing to your repository.
4. Set the pipeline definition to **Pipeline script from SCM** and select Git.
5. Jenkins will automatically pull the [Jenkinsfile](file:///D:/ReadmeEditor/Jenkinsfile), run the build steps, parse dependency and publish metadata, deploy artifacts, and register the build run in Artifactory.

---

## 🌐 Nginx Production Deployment

For production environments, it is recommended to set up **Nginx** as a reverse proxy. This allows Nginx to handle high-performance static asset serving, SSL/TLS termination, Gzip compression, and forward REST API requests directly to the Java backend.

A complete reverse proxy configuration is provided at [nginx.conf](file:///D:/ReadmeEditor/nginx.conf).

### Configuration Summary:
1. **Static Files (`/`):** Set the `root` directive to point to the directory where static web files are extracted. Nginx will serve these files directly with caching enabled (`expires 7d`).
2. **REST API (`/api/`):** Proxies all incoming `/api` requests to the running backend jar at `http://127.0.0.1:8080/api/` while injecting standard forward headers and CORS filters.

### Running in Production:
```bash
# 1. Start the Java backend in headless server mode
java -jar target/readme-editor-1.0.0.jar --server

# 2. Extract static web resources and configure Nginx:
# Copy nginx.conf to your Nginx sites configuration and reload:
sudo systemctl reload nginx
```

---

## 🔧 Troubleshooting

### 🔴 "Redis connection refused"
*   **Cause:** Redis server is not running on port 6379.
*   **Solution:** Make sure your Redis container is running: `docker-compose up -d`. If using local Redis, make sure the `redis-server` process is active.

### 🔴 "JavaFX runtime components are missing"
*   **Cause:** Attempting to run the shaded JAR file directly on a system without the JavaFX runtime libraries configured on the module path.
*   **Solution:** Use the Maven runner: `mvn javafx:run` or run via `mvn clean compile javafx:run`.

### 🔴 "Failed to create staging directory"
*   **Cause:** Current OS user does not have permission to write to their local directory (e.g. `AppData/Local` on Windows).
*   **Solution:** Launch the shell as an Administrator or configure custom permissions.

---

## 🤝 Contributing

1. Fork the project.
2. Create your feature branch (`git checkout -b feature/AmazingFeature`).
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](file:///D:/ReadmeEditor/LICENSE) file for details.

---
<p align="center">Made with ❤️ using Java 21, JavaFX, and Redis</p>
