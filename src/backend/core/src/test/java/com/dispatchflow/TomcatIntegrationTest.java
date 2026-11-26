package com.dispatchflow;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Marks an integration test that needs a real Tomcat: the whole application on a random port,
 * against the same PostgreSQL container setup and with the scheduled jobs off, as {@link
 * IntegrationTest} has it. It starts a Spring context of its own, so use it only for what MockMvc
 * can't show, such as Tomcat's refusal of a request's parameters or its error dispatch; everything
 * else takes {@link IntegrationTest}. It activates the {@code test} profile alone, as that one
 * does.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = IntegrationTest.STAND_IN_DISPATCHER)
@ActiveProfiles(StandInProfiles.TEST)
@Import(TestcontainersConfiguration.class)
public @interface TomcatIntegrationTest {}
