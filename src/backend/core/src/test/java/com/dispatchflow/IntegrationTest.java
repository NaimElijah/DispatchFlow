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
 * <p>It turns the scheduled jobs off, so none runs against the shared database mid-test. #165 or
 * #170, whichever adds the {@code test} profile first, moves this property into it, and with it
 * {@code throwaway-database}'s one setting, which it then deletes: ADR-121 lets the stand-in Actor
 * boot only when {@code test} is the only active profile.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = SchedulingConfiguration.SCHEDULING_ENABLED + "=false")
@AutoConfigureMockMvc
@ActiveProfiles(TestcontainersConfiguration.THROWAWAY_DATABASE)
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {}
