package com.dispatchflow.archfixture.zeta.shared.application;

import java.util.UUID;

/** Rule 6: a class under the kernel's own package may use the shared application package. */
public interface ClockedIds {

    /** The clock the IDs are stamped with. */
    ZetaClock clock();

    /** A new ID. */
    UUID next();
}
