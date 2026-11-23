package com.dispatchflow.archfixture.alpha.domain.shape;

import com.dispatchflow.shared.AggregateRoot;

/** An aggregate a subclass could build without its creation factory. */
public class AggregateWithAProtectedConstructorTakingState extends AggregateRoot {

    private String name;

    protected AggregateWithAProtectedConstructorTakingState(String name) {
        this.name = name;
    }
}
