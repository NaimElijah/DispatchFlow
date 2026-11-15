package com.dispatchflow.archfixture.zeta.lower.application.api;

/** Rule 4: an api interface implemented once in its module's {@code application.service}, under another name. */
public interface ImplementedUnderAnotherName {

    /** Does what a class not named for it implements. */
    void act();
}
