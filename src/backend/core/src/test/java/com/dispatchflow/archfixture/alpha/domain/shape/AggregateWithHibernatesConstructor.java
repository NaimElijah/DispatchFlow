package com.dispatchflow.archfixture.alpha.domain.shape;

import com.dispatchflow.shared.AggregateRoot;

/** Allowed: the protected no-argument constructor Hibernate needs (ADR-126). */
public class AggregateWithHibernatesConstructor extends AggregateRoot {

    protected AggregateWithHibernatesConstructor() {}
}
