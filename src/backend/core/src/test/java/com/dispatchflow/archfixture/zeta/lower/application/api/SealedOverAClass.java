package com.dispatchflow.archfixture.zeta.lower.application.api;

/**
 * Rule 4: a sealed interface that permits a plain class is a port, not a fact's variants, so it is
 * checked like any other api interface.
 */
public sealed interface SealedOverAClass {

    /** Does what its one permitted class implements. */
    void act();

    /**
     * The one implementation: a class, not a record, outside the module's {@code
     * application.service}.
     */
    final class Plain implements SealedOverAClass {

        @Override
        public void act() {}
    }
}
