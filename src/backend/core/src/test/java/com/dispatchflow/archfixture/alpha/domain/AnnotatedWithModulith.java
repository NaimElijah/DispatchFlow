package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.archfixture.modulith.NamedInterface;

/** Rule 1: a module annotation belongs on a package-info only. */
@NamedInterface
public record AnnotatedWithModulith(int amount) {}
