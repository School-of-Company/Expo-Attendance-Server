---
name: docker
description: Dockerfile and docker-compose authoring guide for this project's JVM/Gradle stack — multi-stage builds, layer caching, security best practices, and compose service wiring.
---

# Docker Guide

## Dockerfile

### Multi-stage build

JVM example — the principle (build in one stage, ship the artifact in a slim runtime) is the same everywhere:

```dockerfile
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Layer caching rules

- `COPY` dependency manifests first, run install, then `COPY` source
- Only invalidate layers that actually changed

### Security

- Use specific digest tags, not `latest`
- Run as non-root: `RUN adduser --disabled-password app && USER app`
- Never `COPY . .` before installing dependencies

## docker-compose.yml

The app's own config keys come from its framework (`SPRING_DATASOURCE_URL`, `DATABASE_URL`, …) — read
them off the project rather than copying names from here. What's worth copying is the wiring:
`depends_on` with a real healthcheck, so the app doesn't start against a database that isn't accepting
connections yet.

```yaml
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      DATABASE_URL: postgres://app:app@db:5432/app # replace with this project's key
    depends_on:
      db:
        condition: service_healthy

  db:
    image: postgres:16
    environment:
      POSTGRES_USER: app
      POSTGRES_PASSWORD: app
      POSTGRES_DB: app
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app"]
      interval: 10s
      retries: 5
```

## .dockerignore

Exclude version control, build output, and local env files:

```
.git
*.log
.env*
build/
.gradle/
```
