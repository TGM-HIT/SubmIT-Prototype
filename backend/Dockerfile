# Build context: repository root (checkstyle.xml lives there).
FROM gradle:8.14.3-jdk23 AS build
WORKDIR /workspace
COPY checkstyle.xml ./
COPY backend/build.gradle backend/settings.gradle backend/
WORKDIR /workspace/backend
RUN gradle --no-daemon dependencies > /dev/null
COPY backend/src src
# Tests and style checks gate the image build.
RUN gradle --no-daemon test checkstyleMain bootJar

FROM eclipse-temurin:23-jre
RUN useradd --system --uid 1001 submit
WORKDIR /app
COPY --from=build /workspace/backend/build/libs/*.jar app.jar
USER submit
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s \
  CMD bash -c 'exec 3<>/dev/tcp/localhost/8080' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
