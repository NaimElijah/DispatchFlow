package com.dispatchflow.archfixture.zeta.upper.application.port.out;

import com.dispatchflow.archfixture.zeta.upper.domain.UpperValue;

/** Rule 5: a type in an application package that has no {@code package-info}. */
public interface UpperRecords {

    /** Appends a value. */
    void append(UpperValue value);
}
