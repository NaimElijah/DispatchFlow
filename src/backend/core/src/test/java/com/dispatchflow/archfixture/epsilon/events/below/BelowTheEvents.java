package com.dispatchflow.archfixture.epsilon.events.below;

/**
 * ADR-125 decision 4: a type in a package below a module's {@code events}, which stays flat because
 * it is part of the module's contract. The flat guard must report its package.
 */
public record BelowTheEvents(int count) {}
