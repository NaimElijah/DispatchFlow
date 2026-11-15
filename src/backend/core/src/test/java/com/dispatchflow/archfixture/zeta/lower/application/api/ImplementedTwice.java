package com.dispatchflow.archfixture.zeta.lower.application.api;

/** Rule 4: an api interface must have exactly one implementation, not two. */
public interface ImplementedTwice {

    /** Done by two classes. */
    void act();
}
