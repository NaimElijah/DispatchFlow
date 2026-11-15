package com.dispatchflow.archfixture.zeta.lower.application.api;

/**
 * Rule 4: a sealed interface whose one permitted subclass is an enum models a fact's variants, so
 * it is skipped.
 */
public sealed interface LowerVerdict {

    /** The verdicts, each a constant. */
    enum Kind implements LowerVerdict {
        /** The fact was accepted. */
        ACCEPTED,
        /** The fact was refused. */
        REFUSED
    }
}
