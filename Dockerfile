# Build stage
FROM gradle:8.5-jdk21 AS build
WORKDIR /app

# Copy only gradle files first to cache dependencies
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle gradle/
RUN gradle dependencies --no-daemon

# Copy the rest of the files
COPY . .
RUN gradle buildFatJar --no-daemon

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Add health check
HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:8080/health || exit 1

# Environment variables for JVM
ENV JAVA_OPTS="-Xmx512m -Xms256m"

EXPOSE 8080
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
