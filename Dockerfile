# ── Build stage ──────────────────────────────────────────────
FROM eclipse-temurin:23-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

RUN --mount=type=secret,id=GITHUB_ACTOR \
    --mount=type=secret,id=GITHUB_TOKEN \
    mkdir -p /root/.m2 && \
    printf '<settings><servers><server><id>github</id><username>%s</username><password>%s</password></server></servers></settings>' \
      "$(cat /run/secrets/GITHUB_ACTOR)" "$(cat /run/secrets/GITHUB_TOKEN)" > /root/.m2/settings.xml && \
    ./mvnw dependency:go-offline -U -B

COPY src/ src/
RUN --mount=type=secret,id=GITHUB_ACTOR \
    --mount=type=secret,id=GITHUB_TOKEN \
    printf '<settings><servers><server><id>github</id><username>%s</username><password>%s</password></server></servers></settings>' \
      "$(cat /run/secrets/GITHUB_ACTOR)" "$(cat /run/secrets/GITHUB_TOKEN)" > /root/.m2/settings.xml && \
    ./mvnw clean package -U -DskipTests -B

# ── Runtime stage ────────────────────────────────────────────
FROM eclipse-temurin:23-jre AS runtime
WORKDIR /app

RUN groupadd -r appuser && useradd -r -g appuser appuser && \
    apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/apple_mdm-*.jar app.jar

# Certs directory will be volume-mounted — do NOT bake certs into the image
RUN mkdir -p /app/certs/apple && chown -R appuser:appuser /app
USER appuser

ENV JAVA_OPTS="-Xms512m -Xmx1g"

EXPOSE 8085

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -sf http://localhost:8085/api/apple/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
