package com.dispatchflow.archfixture.alpha.domain;

import java.util.UUID;

/** Rule 3: UUID.randomUUID(). */
public final class GeneratesARandomUuid {

    static UUID id() {
        return UUID.randomUUID();
    }
}
