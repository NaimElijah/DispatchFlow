package com.dispatchflow.archfixture.zeta.lower.application.api;

/** Rule 4: an api interface must be implemented in its own module, not in another. */
public interface ImplementedElsewhere {

    /** Done by another module. */
    void act();
}
