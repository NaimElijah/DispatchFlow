package com.dispatchflow.archfixture.alpha.application;

import com.dispatchflow.archfixture.alpha.domain.AlphaRecord;

/** Allowed: restore of a type that is not an aggregate. */
public final class RestoresANonAggregate {

    static AlphaRecord load() {
        return AlphaRecord.restore(1);
    }
}
