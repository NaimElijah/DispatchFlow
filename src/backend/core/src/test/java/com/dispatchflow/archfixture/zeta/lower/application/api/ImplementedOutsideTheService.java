package com.dispatchflow.archfixture.zeta.lower.application.api;

/** Rule 4: an api interface implemented once in its own module, but outside its {@code application.service}. */
public interface ImplementedOutsideTheService {

    /** Does what an adapter implements. */
    void act();
}
