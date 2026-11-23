package com.dispatchflow.archfixture.alpha.domain.shape;

/** Wrong: a subclass of a concrete root, which builds one through {@code super()}. */
public final class ExtendsAnAggregate extends AggregateWithHibernatesConstructor {

    private ExtendsAnAggregate() {
        super();
    }
}
