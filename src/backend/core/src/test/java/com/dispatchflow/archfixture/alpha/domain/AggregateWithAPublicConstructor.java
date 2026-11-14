package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.Version;

/** An aggregate anyone may build without its creation factory. */
public final class AggregateWithAPublicConstructor extends AggregateRoot {

    public AggregateWithAPublicConstructor() {
        super(Version.NEW);
    }
}
