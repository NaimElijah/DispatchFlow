FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build
# The build fetches Maven and every dependency through Google's mirror of Maven Central, which the
# audit already reads (ADR-134): Central answers shared CI runners with HTTP 429 now and then, and
# CI's infra job builds this image on every push. It failed so on its first run (ADR-151). Only this
# stage reads the two settings; the image below carries neither.
ENV MVNW_REPOURL=https://maven-central.storage-download.googleapis.com/maven2
COPY <<'EOF' /root/.m2/settings.xml
<settings>
  <mirrors>
    <mirror>
      <id>google-maven-central</id>
      <mirrorOf>central</mirrorOf>
      <url>https://maven-central.storage-download.googleapis.com/maven2</url>
    </mirror>
  </mirrors>
</settings>
EOF
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
# Tests run in CI, where Docker is available for the integration tests; the image only packages.
RUN ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
RUN useradd --system --uid 10001 dispatchflow
COPY --from=build /build/target/core-*.jar app.jar
USER dispatchflow
EXPOSE 8080
# The JVM runs in Israel's zone, as every test does (the pom's argLine), whatever zone the host gives
# it: Hibernate binds an Israel-local date and time in the JVM's zone, and a zone whose clock changes
# differ from Israel's would shift one (ADR-088, #250). The migrate step and the core both run this.
ENTRYPOINT ["java", "-Duser.timezone=Asia/Jerusalem", "-jar", "/app/app.jar"]
