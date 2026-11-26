package com.dispatchflow;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Marks an integration test: the whole application against a real PostgreSQL container, driven
 * through {@code MockMvcTester}. Every {@code *IT} uses this one annotation, so they share one
 * cached Spring context and one container instead of starting their own, but for one that needs a
 * real Tomcat, which takes {@link TomcatIntegrationTest} and its context.
 *
 * <p>The core connects as {@code app}, so every adapter call runs on the grants the migrations give
 * it, and Flyway runs as the owner ({@link TestcontainersConfiguration}, ADR-145). A test that must
 * write a row the core can't takes the {@link DatabaseOwner} {@code JdbcClient}.
 *
 * <p>It activates the {@code test} profile alone, whose {@code application-test.properties} turns
 * Flyway on, turns the scheduled jobs off, so none runs against the shared database mid-test, and
 * lets the stand-in session act as the seeded Dispatcher (ADR-140 decision 4, ADR-146 decision 4).
 * So every call under {@code /api/v1} is signed in as that Dispatcher, which {@link
 * #STAND_IN_DISPATCHER} pins against an exported variable. A test that needs another
 * role calls its use case with {@link TestActors}. Should several {@code *IT}s need the stand-in to
 * act as another entry, they share one meta-annotation that sets {@code
 * dispatchflow.stand-in.account}, never a {@code @TestPropertySource} each, which would start a
 * context and a container per class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = IntegrationTest.STAND_IN_DISPATCHER)
@AutoConfigureMockMvc
@ActiveProfiles(StandInProfiles.TEST)
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {

    /**
     * The stand-in's entry, set where it outranks the environment, so a {@code
     * DISPATCHFLOW_STANDIN_ACCOUNT} a developer exported for {@code just core-run} never makes the
     * suite run as another role. A meta-annotation for another entry sets its own.
     */
    String STAND_IN_DISPATCHER = "dispatchflow.stand-in.account=dispatcher";
}
