package com.dispatchflow.archfixture.alpha.domain;

/** An unmarked sealed type: only its {@link Named} variant holds a personal value. */
public sealed interface Recipient {

    /** A recipient known by name. */
    record Named(Name name) implements Recipient {}

    /** A recipient with no name on record. */
    record Anonymous() implements Recipient {}
}
