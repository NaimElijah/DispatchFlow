package com.dispatchflow.archfixture.alpha.domain.id;

import java.util.UUID;
import java.util.function.Supplier;

/** Rule 3: UUID::randomUUID. */
public final class GeneratesARandomUuidByReference {

    static Supplier<UUID> ids() {
        return UUID::randomUUID;
    }
}
