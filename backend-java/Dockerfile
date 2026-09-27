# GearUp backend container image, used for deployment (e.g. on Render).
#
# Factor V (build, release, run): the first stage builds the JAR, the second stage only runs it.
# Factor X (dev/prod parity): the same JDK 25 and Maven 3.9.11 as in development.
# Factor III (config): no configuration or secrets are baked in; they come from environment
# variables set on the hosting platform (see .env.example).

# ---- Build stage ----
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B package

# ---- Run stage: only a Java runtime and the one JAR ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/gearup-backend.jar gearup-backend.jar

# The app listens on $PORT (Factor VII); the platform sets it, 8080 is the fallback.
EXPOSE 8080

# Exec form: Java runs as the main process, so the platform's SIGTERM reaches the
# shutdown hook for a graceful stop (Factor IX).
CMD ["java", "-jar", "gearup-backend.jar"]
