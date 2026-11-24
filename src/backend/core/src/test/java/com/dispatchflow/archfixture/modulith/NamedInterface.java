package com.dispatchflow.archfixture.modulith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stands in for Spring Modulith's annotations, which are not on the classpath before Spring
 * Modulith is added.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
public @interface NamedInterface {}
