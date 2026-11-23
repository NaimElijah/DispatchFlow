package com.dispatchflow.archfixture.alpha.domain.shape;

import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.Version;

/** An aggregate a subclass could build without its creation factory. */
public class AggregateWithAProtectedConstructorTakingState extends AggregateRoot {

    protected AggregateWithAProtectedConstructorTakingState(Version version) {
        super(version);
    }
}
