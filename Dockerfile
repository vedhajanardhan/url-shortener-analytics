# --- Build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:17-jre-alpine

# Run as a non-root user rather than the default root - standard hardening for
# any container that will sit on the public internet.
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/target/url-shortener-1.0.0.jar app.jar
RUN chown app:app app.jar
USER app

EXPOSE 8080

# Render (and most PaaS/orchestrators) can use this directly to know when the
# app is actually ready, not just "the process started".
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:${PORT:-8080}/actuator/health | grep -q '"status":"UP"' || exit 1

# JAVA_OPTS lets memory/GC be tuned per-environment (e.g. Render's free tier
# has limited RAM) without rebuilding the image.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
