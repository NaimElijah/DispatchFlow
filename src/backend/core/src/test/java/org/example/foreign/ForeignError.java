package org.example.foreign;

import com.dispatchflow.shared.DomainError;

/**
 * A {@link DomainError} record outside {@code com.dispatchflow}, which {@code RowErrorsConverter}
 * must refuse to store, because its reader admits no type from another package (ADR-127 decision 7).
 * It sits in a package of its own only for that test: no class under {@code com.dispatchflow} can
 * stand in for it.
 */
public record ForeignError() implements DomainError {}
