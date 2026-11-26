package com.dispatchflow;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Picks the test beans connected as {@code dispatchflow_owner}: its pool, on which Flyway runs, and
 * its {@code JdbcClient} (ADR-145). A test writes a row the core itself would refuse through that
 * client, never through the core's own, and every adapter call stays on {@code app}. Neither bean is
 * a default candidate, so nothing gets one without naming a qualifier.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface DatabaseOwner {}
