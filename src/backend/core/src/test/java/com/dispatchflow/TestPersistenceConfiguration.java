package com.dispatchflow;

import com.dispatchflow.shared.DomainEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.flywaydb.core.api.Location;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.orm.jpa.persistenceunit.ManagedClassNameFilter;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * What the test classpath adds to the persistence setup. {@link TestcontainersConfiguration}
 * imports it, so the integration tests and {@code just core-run} both load it, and both scan the
 * test classpath's entities.
 *
 * <p>The ArchUnit fixtures under {@code archfixture} carry JPA annotations on purpose, some of them
 * deliberately wrong, and Hibernate would map every one it scans, so it scans none of them. The
 * persistence fixtures it does scan get their tables from {@code db/fixture}, whose migrations are
 * numbered {@code V0_*}, a range no real migration takes (ADR-126).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestPersistenceConfiguration {

    private static final String ARCHITECTURE_FIXTURES = "com.dispatchflow.archfixture.";
    private static final Location FIXTURE_MIGRATIONS = new Location("classpath:db/fixture");

    @Bean
    ManagedClassNameFilter noArchitectureFixtures() {
        return className -> !className.startsWith(ARCHITECTURE_FIXTURES);
    }

    @Bean
    FlywayConfigurationCustomizer fixtureMigrations() {
        return configuration -> configuration.locations(
                Stream.concat(Stream.of(configuration.getLocations()), Stream.of(FIXTURE_MIGRATIONS))
                        .toArray(Location[]::new));
    }

    @Bean
    RecordedEvents recordedEvents() {
        return new RecordedEvents();
    }

    /**
     * The domain events the context published, as they were published and once their transaction
     * committed. A listener bound to the commit stands in for Spring Modulith's registry, which
     * keeps a publication only when its transaction commits.
     */
    public static final class RecordedEvents {

        private final List<DomainEvent> published = new CopyOnWriteArrayList<>();
        private final List<DomainEvent> committed = new CopyOnWriteArrayList<>();

        @EventListener
        void onPublished(DomainEvent event) {
            published.add(event);
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void onCommitted(DomainEvent event) {
            committed.add(event);
        }

        /**
         * The events published so far, committed or not.
         *
         * @return a copy, in publication order
         */
        public List<DomainEvent> published() {
            return List.copyOf(published);
        }

        /**
         * The events whose transaction committed.
         *
         * @return a copy, in commit order
         */
        public List<DomainEvent> committed() {
            return List.copyOf(committed);
        }

        /** Forgets every recorded event, so a test sees only its own. */
        public void clear() {
            published.clear();
            committed.clear();
        }
    }
}
