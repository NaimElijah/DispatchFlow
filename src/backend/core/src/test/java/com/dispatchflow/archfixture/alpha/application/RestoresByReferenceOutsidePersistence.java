package com.dispatchflow.archfixture.alpha.application;

import com.dispatchflow.archfixture.alpha.domain.AlphaAggregate;
import java.util.function.LongFunction;

/** Rule 6: AlphaAggregate::restore outside persistence. */
public final class RestoresByReferenceOutsidePersistence {

    static LongFunction<AlphaAggregate> loader() {
        return AlphaAggregate::restore;
    }
}
