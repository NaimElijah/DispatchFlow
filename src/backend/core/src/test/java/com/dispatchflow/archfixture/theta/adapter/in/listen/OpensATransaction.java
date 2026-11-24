package com.dispatchflow.archfixture.theta.adapter.in.listen;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.transaction.annotation.Transactional;

/**
 * An annotation that carries {@code @Transactional}, as Spring Modulith's {@code
 * ApplicationModuleListener} does.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Transactional
public @interface OpensATransaction {}
