# Build Stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
ENV GRADLE_USER_HOME=/app/.gradle

# Build arguments for Hugging Face
ARG HF_BASE_URL
ARG HF_OWNER
ARG HF_REPO
ARG IMAGE_REVISION
ARG IMAGE_REF_NAME

COPY .git .git

# Copy Gradle files
COPY gradlew .
COPY gradle gradle
RUN chmod +x gradlew
COPY build.gradle.kts settings.gradle.kts ./

# Pre-download dependencies (Use a mount cache for the .gradle folder)
RUN --mount=type=cache,target=/app/.gradle \
    ./gradlew build -x test -x bootJar --no-daemon || true

# Sync dataset
RUN --mount=type=secret,id=hf_token \
    --mount=type=cache,target=/app/.gradle \
    export HF_TOKEN=$(cat /run/secrets/hf_token) && \
    if [ -z "$HF_TOKEN" ]; then echo "HF_TOKEN is empty"; exit 1; fi && \
    ./gradlew downloadTestData -PsyncDataset --no-daemon \
    -DHF_BASE_URL=${HF_BASE_URL} \
    -DHF_OWNER=${HF_OWNER} \
    -DHF_REPO=${HF_REPO} \
    -DHF_TOKEN="${HF_TOKEN}"

# Copy source code
COPY src src

# Build application
RUN --mount=type=secret,id=hf_token \
    --mount=type=cache,target=/app/.gradle \
    export HF_TOKEN=$(cat /run/secrets/hf_token) && \
    if [ -z "$HF_TOKEN" ]; then echo "HF_TOKEN is empty"; exit 1; fi && \
    ./gradlew bootJar -x test --no-daemon \
    -PGIT_COMMIT=${IMAGE_REVISION} \
    -PGIT_TAG=${IMAGE_REF_NAME}

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -g 1000 appgroup && adduser -u 1000 -G appgroup -s /bin/sh -D appuser

# Copy JAR and set permissions
COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ARG IMAGE_REVISION
ARG IMAGE_REF_NAME
ARG IMAGE_CREATED

# Metadata
LABEL org.opencontainers.image.revision=$IMAGE_REVISION \
    org.opencontainers.image.ref.name=$IMAGE_REF_NAME \
    org.opencontainers.image.created=$IMAGE_CREATED \
    org.opencontainers.image.title="subscription-killer-api" \
    org.opencontainers.image.description="Spring Boot API for Subscription Killer"

# JVM settings
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]