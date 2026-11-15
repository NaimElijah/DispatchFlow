package com.dispatchflow.archfixture.zeta.lower.application.api;

import com.dispatchflow.archfixture.zeta.shared.ZetaKernelValue;

/** Rule 4: an api interface with one implementation in its own module, beside an abstract base. */
public interface LowerFacts {

    /** The fact kept for a key. */
    LowerFact fact(ZetaKernelValue key);
}
