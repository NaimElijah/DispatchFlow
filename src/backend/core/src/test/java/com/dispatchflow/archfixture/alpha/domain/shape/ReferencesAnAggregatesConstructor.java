package com.dispatchflow.archfixture.alpha.domain.shape;

import java.util.function.Supplier;

/** Wrong: a constructor reference builds the aggregate outside its root as a call would. */
public final class ReferencesAnAggregatesConstructor {

    private ReferencesAnAggregatesConstructor() {}

    public static Supplier<AggregateWithHibernatesConstructor> builder() {
        return AggregateWithHibernatesConstructor::new;
    }
}
