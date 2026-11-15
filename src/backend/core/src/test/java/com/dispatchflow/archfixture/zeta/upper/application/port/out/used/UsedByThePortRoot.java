package com.dispatchflow.archfixture.zeta.upper.application.port.out.used;

/**
 * Rule 7: a subpackage type that the {@code port.out} root uses, and that uses nothing of the root.
 */
public record UsedByThePortRoot(int count) {}
