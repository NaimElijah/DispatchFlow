package com.dispatchflow;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Marks an integration test: the whole application against a real PostgreSQL container, driven
 * through {@code MockMvcTester}. Every {@code *IT} uses this one annotation, so they share one
 * cached Spring context and one container instead of starting their own.
 *
 * <p>It turns the scheduled jobs off, so none runs against the shared database mid-test. #165 or
 * #170, whichever adds the {@code test} profile first, moves this property into it.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = SchedulingConfiguration.SCHEDULING_ENABLED + "=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {}
