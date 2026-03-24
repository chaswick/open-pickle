# ---- Build stage: use the project's Gradle wrapper so builds use the wrapper-pinned Gradle ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Use a persistent cache dir to keep dependencies across builds
ENV GRADLE_USER_HOME=/cache/gradle

# Copy build files and the Gradle wrapper
COPY build.gradle settings.gradle gradlew gradlew.bat ./
COPY gradle gradle
COPY trophy-generator-core/build.gradle trophy-generator-core/
COPY trophy-generator-cli/build.gradle trophy-generator-cli/

# Download dependencies (cached layer)
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies --no-build-cache

# Bring in sources
COPY src src
COPY trophy-generator-core trophy-generator-core
COPY trophy-generator-cli trophy-generator-cli

# Build the application
RUN ./gradlew --no-daemon clean bootJar

# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN useradd -ms /bin/bash spring
RUN mkdir -p /data/trophy-storage && chown spring:spring /data/trophy-storage
# Ensure the logs directory exists and is writable by the 'spring' user so Logback can open transcript files
RUN mkdir -p /app/logs && chown spring:spring /app/logs

# The public build artifact is named 'openpickle' per settings.gradle
COPY --from=build /app/build/libs/openpickle-*.jar /app/app.jar
COPY scripts/ops/wait-for-mysql.sh /app/wait-for-mysql.sh
COPY scripts/ops/docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/wait-for-mysql.sh /app/docker-entrypoint.sh

EXPOSE 8090
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["sh","/app/docker-entrypoint.sh"]
