package com.dispatchflow;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

/**
 * Marks a bean that exists only when the active profiles are exactly {@code dev} or exactly {@code
 * test} ({@link StandInProfiles}): the stand-in session, and each {@code <Port>StandIn} adapter
 * whose real adapter isn't built yet (ADR-140 decision 10, ADR-147). Such an adapter is a Spring
 * component in its port's declared adapter folder, where the real adapter replaces it, and its
 * Javadoc names the issue that does. Never use {@code @Profile({"dev", "test"})} instead: it holds
 * beside any other profile too, and a deployment would then run on a stand-in.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(StandInProfiles.class)
public @interface StandInOnly {}
