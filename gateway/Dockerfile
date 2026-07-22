# Build context is the repository root (this is a multi-module Gradle build) --
# on Render: dockerfilePath=./gateway/Dockerfile, dockerContext=.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :gateway:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/gateway/build/libs/gateway-0.0.1-SNAPSHOT.jar app.jar
# Render's Docker services must bind to $PORT (default 10000) -- see application.yml's
# server.port: ${PORT:8080}. EXPOSE here is documentation only, not enforced by Render.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
