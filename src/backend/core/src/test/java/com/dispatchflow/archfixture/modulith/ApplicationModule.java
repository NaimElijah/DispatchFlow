package com.dispatchflow.archfixture.modulith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stands in for Spring Modulith's {@code @ApplicationModule}, a module annotation other than {@link
 * NamedInterface}, which is not on the classpath before M0.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
public @interface ApplicationModule {}
