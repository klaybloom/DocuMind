# ---------- Stage 1: Build ----------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml .
# Cache dependencies first (layer won't change unless pom.xml changes)
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:17-jre

LABEL maintainer="DocuMind"
LABEL description="DocuMind - RAG-based intelligent document Q&A assistant"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system documind \
    && useradd --system --gid documind --home-dir /app --shell /usr/sbin/nologin documind

WORKDIR /app

COPY --from=builder /build/target/documind-*.jar app.jar

# Create documents directory with proper ownership
RUN mkdir -p /data/documents && chown -R documind:documind /data

# Default environment variables (overridable at runtime)
ENV JAVA_OPTS="-Xms256m -Xmx512m" \
    PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    DOCUMIND_ADMIN_USERNAME=admin \
    DOCUMIND_DB_PATH=/data/documents/.documind-db \
    APP_DOCUMENTS_PATH=/data/documents

EXPOSE 8080

USER documind

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS "http://localhost:${PORT:-8080}/api/v1/health" || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=${PORT:-8080} --app.documents-path=$APP_DOCUMENTS_PATH"]
