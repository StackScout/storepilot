# Multi-stage build — build stage has the JDK + Gradle wrapper, runtime
# stage only ships a JRE + the built jar. config.yml (gitignored local
# secrets) is never copied in — real config comes from env vars, which on
# AWS are supplied by infra/docker/bootstrap.sh from SSM Parameter Store.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --version
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre AS runtime
RUN useradd --system --create-home --shell /usr/sbin/nologin storepilot
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
USER storepilot
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
