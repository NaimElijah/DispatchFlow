package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.AggregateRoot;

/** An aggregate whose only constructor is private, built by its own factory. */
public final class AlphaAggregate extends AggregateRoot {

    private AlphaAggregate() {}

    public static AlphaAggregate open() {
        return new AlphaAggregate();
    }
}
