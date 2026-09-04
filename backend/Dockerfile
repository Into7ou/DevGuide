# syntax=docker/dockerfile:1.7
FROM maven:3.9.16-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine-3.24
RUN addgroup -S devguide && adduser -S -G devguide -h /app devguide
WORKDIR /app
COPY --from=build --chown=devguide:devguide /workspace/target/devguide-0.7.0.jar app.jar
USER devguide
EXPOSE 18080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
