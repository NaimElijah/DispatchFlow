package com.dispatchflow.archfixture.alpha.domain.shape;

/** Wrong: a class in a root's package calls Hibernate's constructor and skips the root's factory. */
public final class BuildsAnAggregateOutsideItsRoot {

    private BuildsAnAggregateOutsideItsRoot() {}

    public static AggregateWithHibernatesConstructor build() {
        return new AggregateWithHibernatesConstructor();
    }
}
