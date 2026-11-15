package com.dispatchflow.archfixture.zeta.lower.application.service;

import com.dispatchflow.archfixture.zeta.lower.application.api.LowerFact;
import com.dispatchflow.archfixture.zeta.shared.ZetaKernelValue;

/** Rule 4: the one implementation of {@code LowerFacts}, in its own module. */
public final class LowerFactsService extends LowerFactsBase {

    @Override
    public LowerFact fact(ZetaKernelValue key) {
        return new LowerFact(key);
    }
}
