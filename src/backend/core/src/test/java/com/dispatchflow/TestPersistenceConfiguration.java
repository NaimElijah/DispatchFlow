package com.dispatchflow;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.persistenceunit.ManagedClassNameFilter;

/**
 * What the test classpath adds to the persistence setup. {@link TestcontainersConfiguration}
 * imports it, so the integration tests and {@code just core-run} both load it.
 *
 * <p>The ArchUnit fixtures under {@code archfixture} carry JPA annotations on purpose, some of them
 * deliberately wrong, and Hibernate would map every one it scans. So it scans none of them.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestPersistenceConfiguration {

    private static final String ARCHITECTURE_FIXTURES = "com.dispatchflow.archfixture.";

    @Bean
    ManagedClassNameFilter noArchitectureFixtures() {
        return className -> !className.startsWith(ARCHITECTURE_FIXTURES);
    }
}
