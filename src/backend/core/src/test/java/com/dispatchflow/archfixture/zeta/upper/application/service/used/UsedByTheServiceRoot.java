package com.dispatchflow.archfixture.zeta.upper.application.service.used;

/** Rule 7: a subpackage type that the {@code service} root uses, and that uses nothing of the root. */
public record UsedByTheServiceRoot(int count) {}
