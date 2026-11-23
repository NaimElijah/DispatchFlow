package com.dispatchflow.archfixture.alpha.domain.shape;

import com.dispatchflow.shared.AggregateRoot;

/** An aggregate anyone may build without its creation factory. */
public final class AggregateWithAPublicConstructor extends AggregateRoot {

    public AggregateWithAPublicConstructor() {
        super();
    }
}
