FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 asyncflow
RUN mkdir -p /app/data/reports && chown -R asyncflow:asyncflow /app
COPY --from=build /workspace/target/asyncflow-*.jar app.jar
USER asyncflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
