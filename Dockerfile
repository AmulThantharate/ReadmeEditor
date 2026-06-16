# =============================================================================
# Stage 1: Build & Package Shaded JAR
# =============================================================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy Maven Project Object Model (POM) and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy project source code
COPY src ./src

# Build the shaded uber-jar (runs compiler, shades dependencies into a single binary, and skips integration tests)
RUN mvn clean package -DskipTests

# =============================================================================
# Stage 2: Minimalist JRE Runtime Image
# =============================================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Add a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy the built shaded JAR from the builder stage
COPY --from=builder /build/target/readme-editor-1.0.0.jar ./app.jar

# Expose the default configured server port
EXPOSE 8080

# Environment variables defaults (can be overridden at runtime)
ENV REDIS_HOST=localhost
ENV REDIS_PORT=6379

# Start the application in headless web-server mode using our custom Launcher
ENTRYPOINT ["java", "-jar", "app.jar", "--server"]
