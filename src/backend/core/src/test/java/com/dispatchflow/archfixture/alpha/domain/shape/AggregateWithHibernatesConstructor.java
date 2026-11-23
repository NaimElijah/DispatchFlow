package com.dispatchflow.archfixture.alpha.domain.shape;

import com.dispatchflow.shared.AggregateRoot;

/**
 * Allowed: the protected no-argument constructor Hibernate needs (ADR-126), which the root's own
 * factory also calls.
 */
public class AggregateWithHibernatesConstructor extends AggregateRoot {

    protected AggregateWithHibernatesConstructor() {}

    public static AggregateWithHibernatesConstructor open() {
        return new AggregateWithHibernatesConstructor();
    }
}
