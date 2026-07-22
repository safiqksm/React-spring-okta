# Build context is the repository root (this is a multi-module Gradle build) --
# on Render: dockerfilePath=./service-3/Dockerfile, dockerContext=.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :service-3:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/service-3/build/libs/service-3-0.0.1-SNAPSHOT.jar app.jar
# Render's Docker services must bind to $PORT (default 10000) -- see application.yml's
# server.port: ${PORT:8083}. EXPOSE here is documentation only, not enforced by Render.
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
