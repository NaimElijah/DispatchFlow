package com.dispatchflow.archfixture.alpha.domain.randomness;

import java.util.Collections;
import java.util.List;

/** Rule 4: Collections.shuffle. */
public final class ShufflesAList {

    static void shuffle(List<String> names) {
        Collections.shuffle(names);
    }
}
