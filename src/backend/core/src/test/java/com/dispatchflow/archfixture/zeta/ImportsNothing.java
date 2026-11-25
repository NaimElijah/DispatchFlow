package com.dispatchflow.archfixture.zeta;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Rule 15: an annotation that carries an {@code @Import}, so a configuration it marks imports
 * through it rather than through its own list. It names nothing, so the rule's other half has no
 * use case to flag on it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import({})
public @interface ImportsNothing {}
