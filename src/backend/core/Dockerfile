# Built from the repository's root, so the build sees the client API's contract, which the core's
# interfaces are generated from at every build (ADR-140 decision 2, ADR-160). The build keeps the
# checkout's layout, so the pom's paths up to the root hold here as they do in a checkout:
# Dockerfile.dockerignore lets in only this folder and that contract.
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build/src/backend/core
# Without unzip, the Maven wrapper fetches the .tar.gz, not the zip whose SHA-256 it pins, which
# Windows, macOS, CI and a Linux machine with unzip fetch (ADR-152). apt checks the package's
# signature.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*
# The build fetches Maven and every dependency through Google's mirror of Maven Central, which the
# audit already reads (ADR-134): Central answers shared CI runners with HTTP 429 now and then, and
# CI's infra job builds this image on every push. It failed so on its first run (ADR-151). Only this
# stage reads the two settings; the image below carries neither. The mirror stands in for every
# repository, those a dependency's pom names included, so the build reaches no third-party feed, and
# the wrapper checks Maven's download against its pinned SHA-256 (ADR-152). The ARG names the
# mirror once for both settings below. ADR-047's pilot image copies CI's jar instead, and drops
# this build with them.
ARG MAVEN_MIRROR=https://maven-central.storage-download.googleapis.com/maven2
ENV MVNW_REPOURL=${MAVEN_MIRROR}
COPY <<EOF /root/.m2/settings.xml
<settings>
  <mirrors>
    <mirror>
      <id>google-maven-central</id>
      <mirrorOf>*</mirrorOf>
      <url>${MAVEN_MIRROR}</url>
    </mirror>
  </mirrors>
</settings>
EOF
COPY src/backend/core/.mvn/ .mvn/
COPY src/backend/core/mvnw src/backend/core/pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline
COPY contracts/client-api/v1/ /build/contracts/client-api/v1/
COPY src/backend/core/src/ src/
# Tests and the formatting check run in CI, where Docker is available for the integration tests; the
# image only packages, so an unformatted file never stops the dev stack from starting.
RUN ./mvnw -B -ntp package -DskipTests -Dspotless.check.skip=true

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
RUN useradd --system --uid 10001 dispatchflow
COPY --from=build /build/src/backend/core/target/core-*.jar app.jar
USER dispatchflow
EXPOSE 8080
# The JVM runs in Israel's zone, as every test does (the pom's argLine), whatever zone the host gives
# it: Hibernate binds an Israel-local date and time in the JVM's zone, and a zone whose clock changes
# differ from Israel's would shift one (ADR-088, #250). The migrate step and the core both run this.
ENTRYPOINT ["java", "-Duser.timezone=Asia/Jerusalem", "-jar", "/app/app.jar"]
