package com.dispatchflow;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** A real PostgreSQL in a container, wired in as the application's datasource. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18.6");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
