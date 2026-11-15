package com.dispatchflow.archfixture.zeta.shared.application;

import java.time.Instant;

/** A shared port, as the production {@code Clock} is. */
public interface ZetaClock {

    /** The current instant. */
    Instant now();
}
