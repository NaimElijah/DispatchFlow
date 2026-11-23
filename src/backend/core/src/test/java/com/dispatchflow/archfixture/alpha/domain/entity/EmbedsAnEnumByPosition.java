package com.dispatchflow.archfixture.alpha.domain.entity;

import jakarta.persistence.Embeddable;

/**
 * Rule: an embeddable's enum is stored by name too.
 *
 * @param kind the enum, with no {@code @Enumerated}
 */
@Embeddable
public record EmbedsAnEnumByPosition(EntityKind kind) {}
