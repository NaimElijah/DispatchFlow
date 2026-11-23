package com.dispatchflow.archfixture.alpha.domain.shape;

import com.dispatchflow.shared.AggregateRoot;

/**
 * A root whose nested variant extends it. The variant calls the constructor from within the root,
 * which the constructor rule allows, so only the rule against extending a root flags it.
 */
public class AggregateWithANestedVariant extends AggregateRoot {

    protected AggregateWithANestedVariant() {}

    /** Wrong: a subclass of a concrete root, though nested in it. */
    public static final class Variant extends AggregateWithANestedVariant {

        private Variant() {
            super();
        }
    }
}
