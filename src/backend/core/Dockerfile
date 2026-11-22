FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build
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
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
