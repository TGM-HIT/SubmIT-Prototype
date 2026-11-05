# Build context: repository root (checkstyle.xml lives there).
# No gradle:*-jdk23 image exists; use a JDK 23 base and the pinned Gradle wrapper.
FROM eclipse-temurin:23-jdk AS build
WORKDIR /workspace
RUN apt-get update \
    && apt-get install -y --no-install-recommends make \
    && rm -rf /var/lib/apt/lists/*
COPY checkstyle.xml ./
COPY Makefile /workspace/Makefile
COPY backend/build.gradle backend/settings.gradle backend/gradlew backend/
COPY backend/gradle backend/gradle
WORKDIR /workspace/backend
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY backend/src src
# Tests replace the workbook reader with synthetic fixtures; provide only an empty path sentinel.
RUN mkdir -p /workspace/docs && : > /workspace/docs/Projekte_2026_27.xlsx
# Tests and style checks gate the image build.
RUN ./gradlew --no-daemon test checkstyleMain bootJar

FROM eclipse-temurin:23-jre
RUN useradd --system --uid 1001 submit
WORKDIR /app
COPY --from=build /workspace/backend/build/libs/*.jar app.jar
USER submit
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s \
  CMD bash -c 'exec 3<>/dev/tcp/localhost/8080' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
