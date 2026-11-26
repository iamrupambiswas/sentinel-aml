# syntax=docker/dockerfile:1.7
#
# One recipe for every Java service. docker-compose.yml builds it five times with a different SERVICE:
#   docker build -f services/Dockerfile --build-arg SERVICE=gateway-service services
# The context is the Maven reactor (services/), so common-lib is built alongside the service (-am).

# ---- build: compile the service and the modules it depends on ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /src
COPY . .
# The cache mount keeps ~/.m2 between builds: the first build downloads dependencies, later ones don't
RUN --mount=type=cache,target=/root/.m2 \
    test -n "$SERVICE" && \
    mvn -B -q -pl "$SERVICE" -am package -DskipTests && \
    cp "$SERVICE/target/$SERVICE"-*.jar /app.jar

# ---- run: JRE only, non-root ----
FROM eclipse-temurin:21-jre
RUN groupadd --system sentinel && useradd --system --gid sentinel --no-create-home sentinel
WORKDIR /app
COPY --from=build /app.jar app.jar
USER sentinel
# Use most of the container's memory limit for the heap, and exit (so Docker restarts it) on OutOfMemory
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
